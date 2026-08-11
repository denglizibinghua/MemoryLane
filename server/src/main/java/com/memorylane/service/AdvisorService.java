package com.memorylane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylane.entity.Contact;
import com.memorylane.entity.UserProfile;
import com.memorylane.repository.AiSettingsRepository;
import com.memorylane.repository.ContactRepository;
import com.memorylane.repository.UserProfileRepository;
import com.memorylane.retrieval.SearchResult;
import com.memorylane.retrieval.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 军师模式 — 回复建议服务。
 *
 * <p>根据对方的历史记忆（关键词检索 top15）和当前对话上下文，调用 LLM
 * 生成自然、得体的回复建议和可继续聊的新话题。
 *
 * <p>系统 & 用户提示词来自 {@link PromptTemplateService}（key={@code advisor.system}
 * 和 {@code advisor.user}）。模板修改即时生效，无需重启。
 */
@Slf4j
@Service
public class AdvisorService {

    private static final int TOP_MEMORIES = 10;
    private static final int MAX_MEMORY_CONTENT_LENGTH = 250;

    /**
     * Style-specific personality injection — prepended to the base system prompt.
     * The base prompt (from PromptTemplateService) contains the JSON format requirements.
     */
    static final Map<String, String> STYLE_PERSONALITY;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("default", "");
        m.put("humorous",
                "你是一个风趣幽默的聊天军师。擅长用俏皮话、梗和适度调侃把对话变得轻松有趣，让对方会心一笑。保持分寸感，不要低俗。\n\n");
        m.put("cute",
                "你是一个软萌可爱的聊天军师。说话带撒娇语气，温暖治愈，多用「～」「啦」「嘛」「呢」等语气词，像小猫一样让人心软。\n\n");
        m.put("gentle",
                "你是一个温柔体贴的聊天军师。善于倾听，回复细腻温暖，让对方感到被充分理解和关心，像一个知心朋友。\n\n");
        m.put("cool",
                "你是一个高冷简洁的聊天军师。话少但句句到位，不废话不啰嗦，偶尔冷笑话，保持高冷但不失礼貌。\n\n");
        m.put("tsundere",
                "你是一个傲娇属性的聊天军师。嘴上嫌弃行动关心，先别扭一下再好好说话。用「哼」「才不是关心你」「随便你」等句式，但一定要在最后流露善意。\n\n");
        STYLE_PERSONALITY = Map.copyOf(m);
    }

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final SearchService searchService;
    private final ContactRepository contactRepository;
    private final UserProfileRepository profileRepository;
    private final AiSettingsRepository aiSettingsRepository;
    private final ObjectMapper objectMapper;

    public AdvisorService(ChatClient.Builder builder,
                          PromptTemplateService promptTemplateService,
                          SearchService searchService,
                          ContactRepository contactRepository,
                          UserProfileRepository profileRepository,
                          AiSettingsRepository aiSettingsRepository,
                          ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.searchService = searchService;
        this.contactRepository = contactRepository;
        this.profileRepository = profileRepository;
        this.aiSettingsRepository = aiSettingsRepository;
        this.objectMapper = objectMapper;
        this.chatClient = builder.build();
    }

    /**
     * 生成回复建议。
     *
     * @param contactId      联系人 ID
     * @param lastMessage    对方最近一条消息
     * @param recentContext  最近的对话上下文（可空）
     * @return 回复建议与话题建议；LLM 调用或解析失败时返回空列表结果
     */
    public SuggestResult suggest(Long contactId, String lastMessage, List<String> recentContext) {
        String contactName = contactRepository.findById(contactId)
                .map(Contact::getName)
                .orElse("对方");

        String profileSection = buildProfileSection();

        List<SearchResult> memories = searchService.keywordSearch(lastMessage, contactId).stream()
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(TOP_MEMORIES)
                .toList();

        String memoriesText = memories.isEmpty()
                ? "（暂无相关记忆）"
                : memories.stream()
                        .map(m -> String.format("- [%s] %s (置信度 %.2f)",
                                m.type(), sanitizeMemoryContent(m.content()), m.score()))
                        .collect(Collectors.joining("\n"));

        List<String> safeContext = (recentContext == null)
                ? List.of()
                : recentContext.stream().filter(Objects::nonNull).toList();
        String contextText = safeContext.isEmpty()
                ? "（无）"
                : String.join("\n", safeContext);

        String basePrompt = promptTemplateService.getTemplate("advisor.system");
        String userName = profileRepository.findFirstByOrderByIdAsc()
                .map(p -> p.getDisplayName())
                .filter(n -> !n.isBlank())
                .orElse("用户");
        String personality = STYLE_PERSONALITY.getOrDefault(
                aiSettingsRepository.findFirst().map(s -> s.getAdvisorStyle()).orElse("default"),
                "");
        String systemPrompt = (personality + basePrompt).replace("{userName}", userName);
        String userTemplate = promptTemplateService.getTemplate("advisor.user");
        String userPrompt = userTemplate
                .replace("{contactName}", contactName)
                .replace("{profileSection}", profileSection)
                .replace("{context}", contextText)
                .replace("{lastMessage}", lastMessage)
                .replace("{memories}", memoriesText);
        log.info("Advisor suggest for contact={}, memories={}", contactId, memories.size());

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text(userPrompt))
                    .call().content();
            log.info("Advisor response: {}", response);
            return parseSuggest(response);
        } catch (Exception e) {
            log.error("Advisor suggest failed for contact={}", contactId, e);
            return new SuggestResult(List.of(), List.of());
        }
    }

    private SuggestResult parseSuggest(String response) {
        try {
            Map<String, Object> root = objectMapper.readValue(
                    extractJson(response), new TypeReference<>() {});
            return new SuggestResult(parseReplies(root.get("replies")), parseTopics(root.get("newTopics")));
        } catch (Exception e) {
            log.warn("Failed to parse advisor JSON: {}", response, e);
            return new SuggestResult(List.of(), List.of());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Reply> parseReplies(Object raw) {
        if (!(raw instanceof List<?> items)) return List.of();
        List<Reply> replies = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> m) {
                replies.add(new Reply(
                        toStringOrEmpty(m.get("style")),
                        toStringOrEmpty(m.get("content")),
                        toStringOrEmpty(m.get("reason"))));
            }
        }
        return replies;
    }

    @SuppressWarnings("unchecked")
    private List<Topic> parseTopics(Object raw) {
        if (!(raw instanceof List<?> items)) return List.of();
        List<Topic> topics = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> m) {
                topics.add(new Topic(
                        toStringOrEmpty(m.get("content")),
                        toStringOrEmpty(m.get("reason"))));
            }
        }
        return topics;
    }

    /**
     * 从 LLM 响应中提取 JSON 对象/数组部分（LLM 可能在 JSON 前后加说明文本）。
     */
    private String extractJson(String response) {
        if (response == null) return "{}";
        String s = response.trim();
        int braceStart = s.indexOf('{');
        if (braceStart >= 0) {
            int braceEnd = s.lastIndexOf('}');
            if (braceEnd > braceStart) {
                return s.substring(braceStart, braceEnd + 1);
            }
        }
        int arrayStart = s.indexOf('[');
        int arrayEnd = s.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return s.substring(arrayStart, arrayEnd + 1);
        }
        return "{}";
    }

    /**
     * 截断并清洗记忆内容：去换行、控制长度，避免破坏 prompt 格式化。
     */
    private String sanitizeMemoryContent(String content) {
        if (content == null) return "";
        String s = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() <= MAX_MEMORY_CONTENT_LENGTH) return s;
        return s.substring(0, MAX_MEMORY_CONTENT_LENGTH) + "…";
    }

    private String toStringOrEmpty(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String buildProfileSection() {
        return profileRepository.findFirstByOrderByIdAsc()
                .map(p -> {
                    StringBuilder sb = new StringBuilder("## 你的身份\n");
                    if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
                        sb.append("你是").append(p.getDisplayName()).append("。");
                    }
                    if (p.getPersona() != null && !p.getPersona().isBlank()) {
                        sb.append("\n").append(p.getPersona());
                    }
                    if (p.getSpeakingStyle() != null && !p.getSpeakingStyle().isBlank()) {
                        sb.append("\n说话风格：").append(p.getSpeakingStyle());
                    }
                    if (p.getRelationshipDefault() != null && !p.getRelationshipDefault().isBlank()) {
                        sb.append("\n和聊天对象的关系：").append(p.getRelationshipDefault());
                    }
                    return sb.toString();
                })
                .orElse("");
    }

    public record Reply(String style, String content, String reason) {}
    public record Topic(String content, String reason) {}
    public record SuggestResult(List<Reply> replies, List<Topic> newTopics) {}
}

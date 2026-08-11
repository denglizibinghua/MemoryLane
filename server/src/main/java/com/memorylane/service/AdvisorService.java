package com.memorylane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylane.config.DelegatingChatModel;
import com.memorylane.entity.Contact;
import com.memorylane.entity.UserProfile;
import com.memorylane.repository.ContactRepository;
import com.memorylane.repository.UserProfileRepository;
import com.memorylane.retrieval.SearchResult;
import com.memorylane.retrieval.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
 * <p>使用专用的 {@link ChatClient}（独立 defaultSystem），不复用提取管线的
 * ChatClient Bean，避免其"聊天记录分析助手"的硬编码系统提示词污染生成结果。
 */
@Slf4j
@Service
public class AdvisorService {

    private static final String ADVISOR_SYSTEM_PROMPT = """
            你是"温同学"的回复助手。你的任务是根据关于对方的历史记忆和当前对话上下文，生成自然、得体的回复建议。

            要求：
            1. 生成 2-3 条回复建议，每条包含 style（风格标签，如"轻松随性""引用约定""关心体贴"）、content（回复文字，自然口语）、reason（为何这样回复，引用哪条记忆）
            2. 生成 1-2 条新话题建议（不回复对方时可以聊什么），每条包含 content（话题内容）、reason（为什么）
            3. 回复风格必须参考对方特征和你们的历史互动
            4. 只输出 JSON，格式: {"replies":[{"style":"...","content":"...","reason":"..."}],"newTopics":[{"content":"...","reason":"..."}]}
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            对方姓名：%s
            %s
            当前对话上下文：
            %s

            对方最近一条消息：%s

            相关历史记忆：
            %s
            """;

    private static final int TOP_MEMORIES = 10;
    private static final int MAX_MEMORY_CONTENT_LENGTH = 250;

    private final ChatClient chatClient;
    private final SearchService searchService;
    private final ContactRepository contactRepository;
    private final UserProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    /**
     * 构建专用的回复建议 ChatClient。
     */
    public AdvisorService(DelegatingChatModel delegatingChatModel,
                          ChatClient.Builder builder,
                          SearchService searchService,
                          ContactRepository contactRepository,
                          UserProfileRepository profileRepository,
                          ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.contactRepository = contactRepository;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
        this.chatClient = builder.defaultSystem(ADVISOR_SYSTEM_PROMPT).build();
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

        String userPrompt = String.format(USER_PROMPT_TEMPLATE, contactName, profileSection, contextText, lastMessage, memoriesText);
        log.info("Advisor suggest for contact={}, memories={}", contactId, memories.size());

        try {
            String response = chatClient.prompt().user(u -> u.text(userPrompt)).call().content();
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

package com.memorylane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylane.config.DelegatingChatModel;
import com.memorylane.entity.UserProfile;
import com.memorylane.repository.MessageRepository;
import com.memorylane.repository.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User profile service — manual persona + AI-analyzed suggestions.
 *
 * <p>Single-row design: one user, one profile. AI analysis reads all
 * self-speaker messages and asks the LLM to infer persona, speaking style,
 * and typical relationship context.
 */
@Slf4j
@Service
public class ProfileService {

    private static final String ANALYZE_PROMPT = """
            分析以下聊天记录中"我"（self）的说话风格、性格特征、身份信息。

            要求：
            1. persona: 一句话描述身份和性格（如"22岁男大学生，青岛上学，计算机专业，性格直接幽默"）
            2. speakingStyle: 说话风格标签（如"幽默/直接/温柔/土味/优雅/理性"）
            3. relationship: 和大多数聊天对象的关系类型（如"同学/朋友/暧昧/同事"）

            只输出JSON: {"persona":"...","speakingStyle":"...","relationship":"..."}
            """;

    private static final int MAX_MSG_LENGTH = 120;
    private static final int MAX_MSGS = 80;

    private final UserProfileRepository profileRepo;
    private final MessageRepository messageRepo;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ProfileService(UserProfileRepository profileRepo,
                          MessageRepository messageRepo,
                          DelegatingChatModel delegatingChatModel,
                          ChatClient.Builder builder,
                          ObjectMapper objectMapper) {
        this.profileRepo = profileRepo;
        this.messageRepo = messageRepo;
        this.objectMapper = objectMapper;
        this.chatClient = builder.defaultSystem(ANALYZE_PROMPT).build();
    }

    @Transactional(readOnly = true)
    public UserProfile getOrCreate() {
        return profileRepo.findFirstByOrderByIdAsc().orElseGet(UserProfile::new);
    }

    @Transactional
    public UserProfile save(UserProfile profile) {
        UserProfile existing = profileRepo.findFirstByOrderByIdAsc().orElse(null);
        if (existing != null) {
            existing.setDisplayName(profile.getDisplayName());
            existing.setPersona(profile.getPersona());
            existing.setSpeakingStyle(profile.getSpeakingStyle());
            existing.setRelationshipDefault(profile.getRelationshipDefault());
            return profileRepo.save(existing);
        }
        return profileRepo.save(profile);
    }

    /**
     * AI analysis: reads self messages, asks LLM to infer persona.
     * Returns suggestion as a map — caller can display before user accepts.
     */
    public Map<String, String> analyze() {
        List<String> lines = messageRepo.findAll().stream()
                .filter(m -> "self".equals(m.getSpeaker()))
                .limit(MAX_MSGS)
                .map(m -> {
                    String content = m.getContent();
                    if (content.length() > MAX_MSG_LENGTH) content = content.substring(0, MAX_MSG_LENGTH) + "…";
                    return content;
                })
                .collect(Collectors.toList());

        if (lines.isEmpty()) {
            return Map.of("hint", "还没有你自己的消息，先导入聊天记录再试试");
        }

        String sample = String.join("\n", lines);
        log.info("Profile analyze: {} self messages", lines.size());

        try {
            String response = chatClient.prompt()
                    .user(u -> u.text("我的聊天记录：\n" + sample))
                    .call().content();
            log.info("Profile analyze response: {}", response);

            Map<String, Object> parsed = objectMapper.readValue(
                    extractJson(response), new TypeReference<>() {});
            return Map.of(
                    "persona", String.valueOf(parsed.getOrDefault("persona", "")),
                    "speakingStyle", String.valueOf(parsed.getOrDefault("speakingStyle", "")),
                    "relationship", String.valueOf(parsed.getOrDefault("relationship", ""))
            );
        } catch (Exception e) {
            log.error("Profile analyze failed", e);
            return Map.of("error", "分析失败: " + e.getMessage());
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        String s = response.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return "{}";
    }
}

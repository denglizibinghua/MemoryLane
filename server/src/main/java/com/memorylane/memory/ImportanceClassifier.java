package com.memorylane.memory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 批量消息重要性分类器。
 * <p>使用 LLM 将消息分为三层：
 * <ul>
 *   <li><b>L1 (1)</b> — 日常寒暄，无需持久化</li>
 *   <li><b>L2 (2)</b> — 有价值信息，保留原文+提取摘要</li>
 *   <li><b>L3 (3)</b> — 关键记忆，需长期保留</li>
 * </ul>
 *
 * <p>输入为消息 ID 和内容列表，返回消息 ID 到重要度的映射。
 */
@Component
public class ImportanceClassifier {

    private final ChatClient chatClient;

    public ImportanceClassifier(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    private static final String PROMPT = """
            分析以下聊天消息，按重要性分为三类：

            1 (日常寒暄) — 打招呼、表情包、无信息量的回应
            2 (有价值) — 包含具体信息、计划、偏好、事件
            3 (关键记忆) — 重要约定、个人信息变更、感情表达、长期承诺

            输出 JSON 数组，只输出有重要性的消息（2 或 3）：
            [{"id": 消息编号, "importance": 2}, ...]

            消息列表：
            %s
            """;

    /**
     * 批处理消息重要性分类。
     *
     * @param messages 消息列表，每项格式为 "id:speaker:content"
     * @return LLM 返回的 JSON 字符串，格式为 [{"id": 1, "importance": 2}, ...]
     */
    public String classify(List<String> messages) {
        String input = String.join("\n", messages);
        return chatClient.prompt()
                .user(u -> u.text(PROMPT.formatted(input)))
                .call()
                .content();
    }
}

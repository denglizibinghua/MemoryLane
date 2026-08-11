package com.memorylane.memory;

import com.memorylane.service.PromptTemplateService;
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
 *
 * <p>系统提示词来自 {@link PromptTemplateService}（key={@code pipeline.system}），
 * 用户提示词来自 key={@code importance.user}，占位符 {@code {messages}} 替换为
 * 消息行列表。模板修改即时生效，无需重启。
 */
@Component
public class ImportanceClassifier {

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;

    public ImportanceClassifier(ChatClient chatClient, PromptTemplateService promptTemplateService) {
        this.chatClient = chatClient;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 批处理消息重要性分类。
     *
     * @param messages 消息列表，每项格式为 "id:speaker:content"
     * @return LLM 返回的 JSON 字符串，格式为 [{"id": 1, "importance": 2}, ...]
     */
    public String classify(List<String> messages) {
        String system = promptTemplateService.getTemplate("pipeline.system");
        String userTemplate = promptTemplateService.getTemplate("importance.user");
        String input = String.join("\n", messages);
        return chatClient.prompt()
                .system(system)
                .user(u -> u.text(userTemplate.replace("{messages}", input)))
                .call()
                .content();
    }
}

package com.memorylane.memory;

import com.memorylane.service.PromptTemplateService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结构化记忆提取器。
 * <p>使用 LLM 从重要的聊天消息中提取六类结构化记忆：
 * promise / personal_info / preference / event / persona / relationship。
 *
 * <p>输出 {@link ExtractedFact} 列表，每条包含类别、内容和置信度。
 *
 * <p>系统提示词来自 {@link PromptTemplateService}（key={@code pipeline.system}），
 * 用户提示词来自 key={@code fact_extractor.user}，占位符 {@code {messages}} 替换为
 * 消息行列表。模板修改即时生效，无需重启。
 */
@Component
public class FactExtractor {

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;

    public FactExtractor(ChatClient chatClient, PromptTemplateService promptTemplateService) {
        this.chatClient = chatClient;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 从消息中提取结构化事实。
     *
     * @param messages 已筛选的重要消息，每项格式为 "speaker:content"
     * @return LLM 返回的 JSON 字符串
     */
    public String extract(List<String> messages) {
        String system = promptTemplateService.getTemplate("pipeline.system");
        String userTemplate = promptTemplateService.getTemplate("fact_extractor.user");
        String input = String.join("\n", messages);
        return chatClient.prompt()
                .system(system)
                .user(u -> u.text(userTemplate.replace("{messages}", input)))
                .call()
                .content();
    }
}

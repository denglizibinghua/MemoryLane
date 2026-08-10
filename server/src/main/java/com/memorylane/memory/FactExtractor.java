package com.memorylane.memory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结构化记忆提取器。
 * <p>使用 LLM 从重要的聊天消息中提取六类结构化记忆：
 * promise / personal_info / preference / event / persona / relationship。
 *
 * <p>输出 {@link ExtractedFact} 列表，每条包含类别、内容和置信度。
 */
@Component
public class FactExtractor {

    private final ChatClient chatClient;

    public FactExtractor(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    private static final String PROMPT = """
            从以下聊天记录中提取结构化事实，每条事实须归类为以下之一：

            - promise：约定、承诺、计划（如"下周去爬山""下个月见面"）
            - personal_info：个人具体信息（如"我在字节实习""我住在朝阳区""手机号138"）
            - preference：偏好、喜好、厌恶（如"不吃香菜""喜欢看悬疑片""讨厌下雨天"）
            - event：已发生的具体事件（如"昨天面试了""上周去看了演唱会""上次吵架是三月"）
            - persona：性格特征、说话习惯、行为模式（如"回复很快""喜欢用～""经常深夜聊天"）
            - relationship：关系动态和评价（如"最近聊天变少了""你俩很默契""他对你一直很体贴"）

            规则：
            1. 每条事实的内容必须是完整的中文句子，能脱离上下文独立理解
            2. 已明确表达的事实 confidence = 0.85-0.95；可推断的 confidence = 0.5-0.7
            3. 不要提取日常寒暄、表情包、拉家常
            4. 同一个人（同一个说话人）说过多次类似内容，取最新的一条，提高 confidence

            只输出一个 JSON 数组：
            [{"category":"preference","content":"喜欢吃川菜，尤其火锅","confidence":0.85}]

            聊天记录：
            %s
            """;

    /**
     * 从消息中提取结构化事实。
     *
     * @param messages 已筛选的重要消息，每项格式为 "speaker:content"
     * @return LLM 返回的 JSON 字符串
     */
    public String extract(List<String> messages) {
        String input = String.join("\n", messages);
        return chatClient.prompt()
                .user(u -> u.text(PROMPT.formatted(input)))
                .call()
                .content();
    }
}

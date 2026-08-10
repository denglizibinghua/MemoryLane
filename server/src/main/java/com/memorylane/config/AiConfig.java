package com.memorylane.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration — ChatClient for structured LLM interactions.
 *
 * <p>ChatModel is auto-configured from {@code spring.ai.openai.*} properties
 * in application.yml. This config exposes a {@link ChatClient} bean with
 * sensible defaults for MemoryLane's extraction pipelines.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个聊天记录分析助手，负责从对话中提取结构化信息。"
                        + "只输出要求的 JSON 格式，不要额外解释。")
                .build();
    }
}

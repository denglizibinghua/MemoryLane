package com.memorylane.config;

import com.memorylane.service.AiSettingsService;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI configuration — ChatClient for structured LLM interactions.
 *
 * <p>The ChatModel is provided by {@link DelegatingChatModel}, which is the
 * single ChatModel bean in the context and auto-detected by the
 * {@link ChatClient.Builder} auto-configuration. The active provider model is
 * swapped in dynamically from the DB-backed settings, so no restart is needed
 * to switch between OpenAI / Ollama / Anthropic / DashScope / ZhiPu / Moonshot.
 */
@Configuration
public class AiConfig {

    private final AiSettingsService aiSettingsService;

    public AiConfig(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    /**
     * Load the persisted provider settings at startup so the app comes up with
     * the last-configured provider already active.
     */
    @PostConstruct
    public void initDefaultProvider() {
        aiSettingsService.initDefaultProvider();
    }

    @Bean
    public ChatClient chatClient(DelegatingChatModel delegatingChatModel, ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个聊天记录分析助手，负责从对话中提取结构化信息。"
                        + "只输出要求的 JSON 格式，不要额外解释。")
                .build();
    }
}

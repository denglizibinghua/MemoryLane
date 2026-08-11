package com.memorylane.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI configuration — ChatClient + DelegatingEmbeddingModel beans.
 *
 * <p>The ChatModel is provided by {@link DelegatingChatModel}, the single
 * ChatModel bean auto-detected by {@link ChatClient.Builder} auto-config.
 * The EmbeddingModel is {@link DelegatingEmbeddingModel} — always present,
 * delegates to a real model only when the user enables semantic search
 * in Settings. Both are hot-swappable from DB-backed settings.
 *
 * <p>Startup initialization of persisted provider settings is handled by
 * {@code AiSettingsService#initDefaultProvider() @PostConstruct}.
 */
@Configuration
public class AiConfig {

    /**
     * The always-present EmbeddingModel bean. When embedding is disabled
     * (default), it returns empty results; when enabled, it delegates to
     * the provider selected in Settings.
     */
    @Bean
    @Primary
    public DelegatingEmbeddingModel delegatingEmbeddingModel() {
        return new DelegatingEmbeddingModel();
    }

    @Bean
    public ChatClient chatClient(DelegatingChatModel delegatingChatModel, ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是一个聊天记录分析助手，负责从对话中提取结构化信息。"
                        + "只输出要求的 JSON 格式，不要额外解释。")
                .build();
    }
}

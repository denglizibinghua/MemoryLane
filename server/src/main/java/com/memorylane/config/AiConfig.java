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

    /**
     * Shared ChatClient bean — no hardcoded defaultSystem.
     *
     * <p>System prompts are now served by {@link com.memorylane.service.PromptTemplateService}
     * and injected per-call by consumers (ImportanceClassifier, FactExtractor).
     * This ensures template edits take effect immediately without restart.
     */
    @Bean
    public ChatClient chatClient(DelegatingChatModel delegatingChatModel, ChatClient.Builder builder) {
        return builder.build();
    }
}

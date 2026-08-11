package com.memorylane.service;

import com.memorylane.config.DelegatingChatModel;
import com.memorylane.config.DelegatingEmbeddingModel;
import com.memorylane.config.DynamicChatModelFactory;
import com.memorylane.config.DynamicEmbeddingModelFactory;
import com.memorylane.entity.AiSettings;
import com.memorylane.repository.AiSettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the DB-backed AI provider + embedding settings and hot-swaps the
 * active {@link ChatModel} and {@link EmbeddingModel} so changes take effect
 * immediately.
 */
@Service
@Slf4j
public class AiSettingsService {

    private static final Long SETTINGS_ID = 1L;

    private final AiSettingsRepository repository;
    private final DynamicChatModelFactory chatModelFactory;
    private final DynamicEmbeddingModelFactory embeddingModelFactory;
    private final DelegatingChatModel delegatingChatModel;
    private final DelegatingEmbeddingModel delegatingEmbeddingModel;

    public AiSettingsService(AiSettingsRepository repository,
                             DynamicChatModelFactory chatModelFactory,
                             DynamicEmbeddingModelFactory embeddingModelFactory,
                             DelegatingChatModel delegatingChatModel,
                             DelegatingEmbeddingModel delegatingEmbeddingModel) {
        this.repository = repository;
        this.chatModelFactory = chatModelFactory;
        this.embeddingModelFactory = embeddingModelFactory;
        this.delegatingChatModel = delegatingChatModel;
        this.delegatingEmbeddingModel = delegatingEmbeddingModel;
    }

    /**
     * Load the persisted settings at startup and activate the saved provider.
     * Falls back to {@code LLM_API_KEY} / {@code LLM_MODEL} environment
     * variables when no API key / model is stored in the DB.
     */
    @PostConstruct
    public void initDefaultProvider() {
        AiSettings settings = repository.findById(SETTINGS_ID).orElse(null);
        if (settings == null) {
            return;
        }
        if (isBlank(settings.getApiKey())) {
            String envKey = System.getenv("LLM_API_KEY");
            if (!isBlank(envKey)) {
                settings.setApiKey(envKey);
            }
        }
        if (isBlank(settings.getModel())) {
            String envModel = System.getenv("LLM_MODEL");
            if (!isBlank(envModel)) {
                settings.setModel(envModel);
            }
        }
        rebuildAndSwap(settings);
        rebuildEmbedding(settings);
    }

    /**
     * Return the current settings with the API key masked, plus the list of
     * supported providers with metadata.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSettings() {
        AiSettings settings = loadOrCreateDefault();
        return buildResponse(settings, true);
    }

    /**
     * Persist updated settings (row id=1) and hot-swap the active ChatModel.
     * A blank API key or one starting with "***" keeps the existing stored key.
     */
    @Transactional
    public Map<String, Object> updateSettings(AiSettingsDTO dto) {
        AiSettings settings = loadOrCreateDefault();

        if (!isBlank(dto.getProvider())) {
            settings.setProvider(dto.getProvider());
        }
        if (dto.getApiBase() != null) {
            settings.setApiBase(dto.getApiBase());
        }
        if (!isBlank(dto.getModel())) {
            settings.setModel(dto.getModel());
        }
        if (dto.getTemperature() != null) {
            settings.setTemperature(dto.getTemperature());
        }
        if (!isBlank(dto.getApiKey()) && !dto.getApiKey().startsWith("***")) {
            settings.setApiKey(dto.getApiKey());
        }
        if (dto.getEmbeddingEnabled() != null) {
            settings.setEmbeddingEnabled(dto.getEmbeddingEnabled());
        }
        if (dto.getEmbeddingProvider() != null) {
            settings.setEmbeddingProvider(dto.getEmbeddingProvider());
        }
        if (dto.getEmbeddingModel() != null) {
            settings.setEmbeddingModel(dto.getEmbeddingModel());
        }
        settings.setUpdatedAt(LocalDateTime.now());

        AiSettings saved = repository.save(settings);
        rebuildAndSwap(saved);
        rebuildEmbedding(saved);
        return buildResponse(saved, true);
    }

    private Map<String, Object> buildResponse(AiSettings settings, boolean maskKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", settings.getProvider());
        result.put("apiKey", maskKey ? maskApiKey(settings.getApiKey()) : settings.getApiKey());
        result.put("apiBase", settings.getApiBase());
        result.put("model", settings.getModel());
        result.put("temperature", settings.getTemperature());
        result.put("embeddingEnabled", settings.getEmbeddingEnabled() != null && settings.getEmbeddingEnabled());
        result.put("embeddingProvider", settings.getEmbeddingProvider());
        result.put("embeddingModel", settings.getEmbeddingModel());
        result.put("embeddingActive", delegatingEmbeddingModel.isEnabled());
        result.put("providers", buildProviderInfoList());
        result.put("embeddingProviders", buildEmbeddingProviderInfoList());
        return result;
    }

    private List<Map<String, Object>> buildProviderInfoList() {
        return List.of(
                providerInfo("openai", "OpenAI", "OpenAI GPT-4o / o4-mini", "gpt-4o-mini", "https://api.openai.com", true),
                providerInfo("deepseek", "DeepSeek", "DeepSeek V3 / R1 (OpenAI 兼容)", "deepseek-chat", "https://api.deepseek.com", true),
                providerInfo("ollama", "Ollama (本地)", "本地运行 Qwen / Llama / DeepSeek", "qwen2.5:7b", "http://localhost:11434", false),
                providerInfo("anthropic", "Anthropic", "Claude 3.5 / 4 系列", "claude-3-5-sonnet-latest", "https://api.anthropic.com", true),
                providerInfo("dashscope", "通义千问", "阿里百炼 DashScope (OpenAI 兼容)", "qwen-plus", "", true),
                providerInfo("zhipuai", "智谱 GLM", "智谱 AI GLM-4 系列", "glm-4-flash", "", true),
                providerInfo("moonshot", "Kimi (月之暗面)", "Moonshot v1 系列", "moonshot-v1-8k", "https://api.moonshot.cn", true),
                providerInfo("custom", "自定义 (OpenAI 兼容)", "第三方中转站 / 自部署 OpenAI 兼容 API", "gpt-4o-mini", "", true)
        );
    }

    private static Map<String, Object> providerInfo(String key, String name, String description,
                                                     String defaultModel, String defaultBaseUrl, boolean needsApiKey) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("key", key);
        info.put("name", name);
        info.put("description", description);
        info.put("defaultModel", defaultModel);
        info.put("defaultBaseUrl", defaultBaseUrl);
        info.put("needsApiKey", needsApiKey);
        return info;
    }

    /**
     * Verify a provider configuration by sending a test prompt through a
     * throw-away ChatModel built from the given (not yet persisted) settings.
     */
    public Map<String, Object> testConnection(AiSettingsDTO dto) {
        try {
            AiSettings testSettings = loadOrCreateDefault();
            if (dto.getProvider() != null) {
                testSettings.setProvider(dto.getProvider());
            }
            if (dto.getApiBase() != null) {
                testSettings.setApiBase(dto.getApiBase());
            }
            if (!isBlank(dto.getModel())) {
                testSettings.setModel(dto.getModel());
            }
            if (dto.getTemperature() != null) {
                testSettings.setTemperature(dto.getTemperature());
            }
            if (!isBlank(dto.getApiKey())) {
                testSettings.setApiKey(dto.getApiKey());
            } else if (isBlank(testSettings.getApiKey())) {
                testSettings.setApiKey(System.getenv("LLM_API_KEY"));
            }
            if (isBlank(testSettings.getModel())) {
                testSettings.setModel(System.getenv("LLM_MODEL"));
            }

            ChatModel model = chatModelFactory.create(testSettings);
            model.call(new Prompt("ping"));

            return Map.of("success", true, "message", "连接成功");
        } catch (Exception e) {
            return Map.of("success", false, "message",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void rebuildAndSwap(AiSettings settings) {
        ChatModel model = chatModelFactory.create(settings);
        delegatingChatModel.setDelegate(model);
    }

    /**
     * Rebuild the EmbeddingModel from DB settings. If embedding is disabled
     * or the provider is not supported for embeddings, clear the delegate
     * so SemanticSearch falls back to keyword-only.
     */
    private void rebuildEmbedding(AiSettings settings) {
        if (settings.getEmbeddingEnabled() == null || !settings.getEmbeddingEnabled()) {
            delegatingEmbeddingModel.setDelegate(null);
            return;
        }
        try {
            EmbeddingModel model = embeddingModelFactory.create(settings);
            delegatingEmbeddingModel.setDelegate(model);
        } catch (Exception e) {
            log.warn("Failed to build embedding model: {}", e.getMessage());
            delegatingEmbeddingModel.setDelegate(null);
        }
    }

    private List<Map<String, Object>> buildEmbeddingProviderInfoList() {
        return List.of(
                providerInfo("openai", "OpenAI", "text-embedding-3-small (1536维)", "text-embedding-3-small", null, true),
                providerInfo("zhipuai", "智谱 GLM", "embedding-2 (1024维)", "embedding-2", null, true),
                providerInfo("ollama", "Ollama (本地)", "nomic-embed-text / bge-m3 等", "nomic-embed-text", null, false)
        );
    }

    private AiSettings loadOrCreateDefault() {
        return repository.findFirst().orElseGet(() -> {
            AiSettings fresh = new AiSettings();
            fresh.setId(SETTINGS_ID);
            fresh.setProvider(DynamicChatModelFactory.PROVIDER_OPENAI);
            fresh.setModel("gpt-4o-mini");
            fresh.setTemperature(0.3);
            fresh.setCreatedAt(LocalDateTime.now());
            fresh.setUpdatedAt(LocalDateTime.now());
            return repository.save(fresh);
        });
    }

    private AiSettings copyOf(AiSettings source) {
        AiSettings copy = new AiSettings();
        copy.setId(source.getId());
        copy.setProvider(source.getProvider());
        copy.setApiKey(source.getApiKey());
        copy.setApiBase(source.getApiBase());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    /**
     * Mask an API key, showing only the first 5 and last 4 characters
     * (e.g. {@code sk-12...xyz1}).
     */
    private String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return null;
        }
        if (apiKey.length() <= 9) {
            return "***";
        }
        return apiKey.substring(0, 5) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Request payload for reading/updating/testing the AI provider settings. */
    @Data
    public static class AiSettingsDTO {
        private String provider;
        private String apiKey;
        private String apiBase;
        private String model;
        private Double temperature;
        private Boolean embeddingEnabled;
        private String embeddingProvider;
        private String embeddingModel;
    }
}

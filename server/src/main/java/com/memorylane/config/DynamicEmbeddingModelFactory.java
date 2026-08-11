package com.memorylane.config;

import com.memorylane.entity.AiSettings;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.stereotype.Component;

/**
 * Builds an {@link EmbeddingModel} for the provider selected in DB settings.
 *
 * <p>Only providers that support embeddings are wired here:
 * OpenAI, ZhiPuAI (智谱), and Ollama (local).
 */
@Component
public class DynamicEmbeddingModelFactory {

    public static final String EMBEDDING_OPENAI = "openai";
    public static final String EMBEDDING_ZHIPUAI = "zhipuai";
    public static final String EMBEDDING_OLLAMA = "ollama";

    public static final String[] SUPPORTED_EMBEDDING_PROVIDERS = {
            EMBEDDING_OPENAI,
            EMBEDDING_ZHIPUAI,
            EMBEDDING_OLLAMA
    };

    private final ObservationRegistry observationRegistry;

    public DynamicEmbeddingModelFactory(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /**
     * Build an EmbeddingModel. Returns {@code null} if the provider is not
     * supported for embeddings.
     */
    public EmbeddingModel create(AiSettings settings) {
        String provider = settings.getEmbeddingProvider() == null
                ? EMBEDDING_OPENAI
                : settings.getEmbeddingProvider().toLowerCase();

        String model = settings.getEmbeddingModel();
        String apiKey = settings.getApiKey();

        return switch (provider) {
            case EMBEDDING_OPENAI -> createOpenAi(apiKey, model);
            case EMBEDDING_ZHIPUAI -> createZhiPuAi(apiKey, model);
            case EMBEDDING_OLLAMA -> createOllama(settings.getApiBase(), model);
            default -> null;
        };
    }

    private EmbeddingModel createOpenAi(String apiKey, String model) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(coalesce(apiKey, ""))
                .baseUrl("https://api.openai.com")
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(coalesce(model, "text-embedding-3-small"))
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    private EmbeddingModel createZhiPuAi(String apiKey, String model) {
        ZhiPuAiApi api = new ZhiPuAiApi(coalesce(apiKey, ""));
        ZhiPuAiEmbeddingOptions options = ZhiPuAiEmbeddingOptions.builder()
                .model(coalesce(model, "embedding-2"))
                .build();
        return new ZhiPuAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    private EmbeddingModel createOllama(String baseUrl, String model) {
        OllamaApi api = new OllamaApi(coalesce(baseUrl, "http://localhost:11434"));
        return OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(coalesce(model, "nomic-embed-text"))
                        .build())
                .observationRegistry(observationRegistry)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }

    private String coalesce(String value, String defaultVal) {
        return (value != null && !value.isBlank()) ? value : defaultVal;
    }
}

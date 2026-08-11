package com.memorylane.config;

import com.memorylane.entity.AiSettings;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.moonshot.MoonshotChatModel;
import org.springframework.ai.moonshot.MoonshotChatOptions;
import org.springframework.ai.moonshot.api.MoonshotApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link ChatModel} for the provider selected in the DB settings.
 *
 * <p>Each provider is constructed programmatically (builder or constructor,
 * matching the exact Spring AI 1.0.0-M6 API surface), so switching providers
 * only requires rebuilding a model and swapping it into the
 * {@link DelegatingChatModel} — no restart needed.
 */
@Component
public class DynamicChatModelFactory {

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_OLLAMA = "ollama";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_ZHIPUAI = "zhipuai";
    public static final String PROVIDER_MOONSHOT = "moonshot";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_CUSTOM = "custom";

    /** Supported provider ids, in display order. */
    public static final String[] SUPPORTED_PROVIDERS = {
            PROVIDER_OPENAI,
            PROVIDER_DEEPSEEK,
            PROVIDER_OLLAMA,
            PROVIDER_ANTHROPIC,
            PROVIDER_DASHSCOPE,
            PROVIDER_ZHIPUAI,
            PROVIDER_MOONSHOT,
            PROVIDER_CUSTOM
    };

    /**
     * Create a ChatModel for the provider described by the given settings.
     *
     * @param settings provider configuration (provider, apiKey, apiBase, model, temperature)
     * @return a fully wired ChatModel for the selected provider
     * @throws IllegalArgumentException if the provider id is unknown
     */
    public ChatModel create(AiSettings settings) {
        String provider = settings.getProvider() == null
                ? PROVIDER_OPENAI
                : settings.getProvider().toLowerCase();

        return switch (provider) {
            case PROVIDER_OPENAI -> createOpenAi(settings);
            case PROVIDER_OLLAMA -> createOllama(settings);
            case PROVIDER_ANTHROPIC -> createAnthropic(settings);
            case PROVIDER_DASHSCOPE -> createDashScope(settings);
            case PROVIDER_ZHIPUAI -> createZhiPu(settings);
            case PROVIDER_MOONSHOT -> createMoonshot(settings);
            case PROVIDER_DEEPSEEK -> createDeepSeek(settings);
            case PROVIDER_CUSTOM -> createCustom(settings);
            default -> throw new IllegalArgumentException(
                    "Unknown AI provider: " + settings.getProvider() + " (supported: "
                            + String.join(", ", SUPPORTED_PROVIDERS) + ")");
        };
    }

    private ChatModel createOpenAi(AiSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey(settings.getApiKey()))
                .baseUrl(coalesce(settings.getApiBase(), "https://api.openai.com"))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(settings.getModel())
                        .temperature(settings.getTemperature())
                        .build())
                .build();
    }

    private ChatModel createOllama(AiSettings settings) {
        OllamaApi api = new OllamaApi(coalesce(settings.getApiBase(), "http://localhost:11434"));
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(settings.getModel())
                        .temperature(settings.getTemperature())
                        .build())
                .build();
    }

    private ChatModel createAnthropic(AiSettings settings) {
        // M6 has no AnthropicApi.Builder — use the (apiKey, baseUrl) constructor.
        AnthropicApi api = new AnthropicApi(apiKey(settings.getApiKey()),
                coalesce(settings.getApiBase(), "https://api.anthropic.com"));
        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(settings.getModel())
                        .temperature(settings.getTemperature())
                        .build())
                .build();
    }

    /**
     * DashScope/通义 via its official OpenAI-compatible endpoint.
     *
     * <p>Spring AI 1.0.0-M6 does not ship a DashScope module (it lives in the
     * separate spring-ai-alibaba project, built against Spring AI 1.1.0+ and
     * incompatible with our M6 core). Alibaba officially exposes DashScope as an
     * OpenAI-compatible API, so we reuse OpenAiApi/OpenAiChatModel pointed at
     * {@code https://dashscope.aliyuncs.com/compatible-mode/v1}.
     */
    private ChatModel createDashScope(AiSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey(settings.getApiKey()))
                .baseUrl(coalesce(settings.getApiBase(),
                        "https://dashscope.aliyuncs.com/compatible-mode/v1"))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(coalesce(settings.getModel(), "qwen-plus"))
                        .temperature(settings.getTemperature())
                        .build())
                .build();
    }

    /**
     * DeepSeek via its OpenAI-compatible endpoint.
     *
     * <p>DeepSeek V3 / R1 are fully compatible with the OpenAI API format,
     * so we reuse OpenAiApi/OpenAiChatModel pointed at
     * {@code https://api.deepseek.com}.
     */
    private ChatModel createDeepSeek(AiSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey(settings.getApiKey()))
                .baseUrl(coalesce(settings.getApiBase(), "https://api.deepseek.com"))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(coalesce(settings.getModel(), "deepseek-chat"))
                        .temperature(settings.getTemperature())
                        .build())
                .build();
    }

    /**
     * Custom OpenAI-compatible provider (third-party proxy / self-hosted).
     * Uses the same OpenAiApi under the hood — the user provides the base URL.
     */
    private ChatModel createCustom(AiSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey(settings.getApiKey()))
                .baseUrl(coalesce(settings.getApiBase(), "https://api.openai.com"))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(coalesce(settings.getModel(), "gpt-4o-mini"))
                        .temperature(settings.getTemperature())
                        .build())
                .build();
    }

    private ChatModel createZhiPu(AiSettings settings) {
        // M6 has no ZhiPuAiApi.Builder and no ZhiPuAiChatModel.Builder —
        // use the (apiKey) constructor and the (api, options) constructor.
        ZhiPuAiApi api = new ZhiPuAiApi(apiKey(settings.getApiKey()));
        return new ZhiPuAiChatModel(api, ZhiPuAiChatOptions.builder()
                .model(settings.getModel())
                .temperature(settings.getTemperature())
                .build());
    }

    private ChatModel createMoonshot(AiSettings settings) {
        // M6 has no MoonshotApi.Builder and no MoonshotChatModel.Builder —
        // use the (apiKey, baseUrl) constructor and the (api, options) constructor.
        MoonshotApi api = new MoonshotApi(apiKey(settings.getApiKey()),
                coalesce(settings.getApiBase(), "https://api.moonshot.cn"));
        return new MoonshotChatModel(api, MoonshotChatOptions.builder()
                .model(settings.getModel())
                .temperature(settings.getTemperature())
                .build());
    }

    private String coalesce(String value, String defaultVal) {
        return (value != null && !value.isBlank()) ? value : defaultVal;
    }

    /**
     * Provider APIs reject {@code null} api keys at build time (Assert.notNull).
     * Coerce to an empty string so a not-yet-configured provider still constructs;
     * actual requests then fail with a clear HTTP error instead of crashing startup.
     */
    private String apiKey(String key) {
        return key != null ? key : "";
    }
}

package com.memorylane.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * A hot-swappable {@link ChatModel} facade.
 *
 * <p>This is the single ChatModel bean in the application context. It holds a
 * volatile reference to the provider-backed model and forwards every call to
 * it, so changing providers (via {@link #setDelegate(ChatModel)}) takes effect
 * immediately for all consumers — no restart required.
 */
@Component
public class DelegatingChatModel implements ChatModel {

    private volatile ChatModel delegate;

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatModel model = requireDelegate();
        return model.call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return requireDelegate().stream(prompt);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        ChatModel model = delegate;
        return model != null ? model.getDefaultOptions() : null;
    }

    /**
     * Swap the active provider model. Safe to call at any time from any thread;
     * in-flight requests finish against the old model, new requests use the new one.
     */
    public void setDelegate(ChatModel model) {
        this.delegate = model;
    }

    /** @return the currently active provider model, or {@code null} if not configured yet. */
    public ChatModel getDelegate() {
        return delegate;
    }

    private ChatModel requireDelegate() {
        ChatModel model = delegate;
        if (model == null) {
            throw new IllegalStateException("No AI provider configured. Please configure in Settings.");
        }
        return model;
    }
}

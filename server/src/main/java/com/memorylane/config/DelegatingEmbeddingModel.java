package com.memorylane.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * Always-present {@link EmbeddingModel} bean that delegates to a real model
 * when embedding is enabled, or returns empty results when disabled.
 *
 * <p>This mirrors {@link DelegatingChatModel} — a single bean always in the
 * context, so consumers like {@code SemanticSearch} never need null checks
 * or {@code ObjectProvider} tricks.
 */
public class DelegatingEmbeddingModel implements EmbeddingModel {

    private volatile EmbeddingModel delegate;

    public void setDelegate(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    public boolean isEnabled() {
        return delegate != null;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (delegate == null) {
            return new EmbeddingResponse(List.of());
        }
        return delegate.call(request);
    }

    @Override
    public float[] embed(String text) {
        if (delegate == null) {
            return null;
        }
        return delegate.embed(text);
    }

    @Override
    public float[] embed(Document document) {
        if (delegate == null) {
            return null;
        }
        return delegate.embed(document);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (delegate == null) {
            return List.of();
        }
        return delegate.embed(texts);
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
        if (delegate == null) {
            return List.of();
        }
        return delegate.embed(documents, options, batchingStrategy);
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
        if (delegate == null) {
            return new EmbeddingResponse(List.of());
        }
        return delegate.embedForResponse(texts);
    }

    @Override
    public int dimensions() {
        if (delegate == null) {
            return 0;
        }
        return delegate.dimensions();
    }
}

package com.memorylane.controller;

import com.memorylane.entity.Memory;
import com.memorylane.repository.MemoryRepository;
import com.memorylane.retrieval.SemanticSearch;
import com.memorylane.service.AiSettingsService;
import com.memorylane.service.TesseractOcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI provider settings REST API.
 *
 * <p>Configure the active AI provider (OpenAI / Ollama / Anthropic /
 * DashScope / ZhiPu / Moonshot). Changes are persisted to the {@code ai_settings}
 * table and take effect immediately — no restart needed.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings")
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;
    private final TesseractOcrService tesseractOcrService;
    private final SemanticSearch semanticSearch;
    private final MemoryRepository memoryRepository;
    private final boolean ocrFallbackEnabled;

    public AiSettingsController(AiSettingsService aiSettingsService,
                                TesseractOcrService tesseractOcrService,
                                SemanticSearch semanticSearch,
                                MemoryRepository memoryRepository,
                                @Value("${memorylane.ocr.fallback-enabled:true}") boolean ocrFallbackEnabled) {
        this.aiSettingsService = aiSettingsService;
        this.tesseractOcrService = tesseractOcrService;
        this.semanticSearch = semanticSearch;
        this.memoryRepository = memoryRepository;
        this.ocrFallbackEnabled = ocrFallbackEnabled;
    }

    /** Current settings (API key masked) + supported providers. */
    @GetMapping("/ai")
    public Map<String, Object> getAiSettings() {
        return aiSettingsService.getSettings();
    }

    /** Update and hot-swap the active provider. */
    @PutMapping("/ai")
    public Map<String, Object> updateAiSettings(@RequestBody AiSettingsService.AiSettingsDTO dto) {
        return aiSettingsService.updateSettings(dto);
    }

    /** Verify a provider configuration with a test request. */
    @PostMapping("/ai/test")
    public Map<String, Object> testConnection(@RequestBody AiSettingsService.AiSettingsDTO dto) {
        return aiSettingsService.testConnection(dto);
    }

    /** Report OCR fallback status (Tesseract availability, fallback enabled). */
    @GetMapping("/ocr-status")
    public Map<String, Object> ocrStatus() {
        return Map.of(
                "tesseractAvailable", tesseractOcrService.isAvailable(),
                "fallbackEnabled", ocrFallbackEnabled
        );
    }

    /**
     * Reindex all memories — generate embeddings for memories that don't have one.
     * Only works when semantic search / embedding is enabled.
     */
    @PostMapping("/reindex-embeddings")
    public Map<String, Object> reindexEmbeddings() {
        List<Memory> all = memoryRepository.findAll();
        int generated = 0;
        int skipped = 0;
        for (Memory m : all) {
            try {
                if (m.getContent() == null || m.getContent().isBlank()) {
                    skipped++;
                    continue;
                }
                semanticSearch.generateAndStore(m);
                generated++;
            } catch (Exception e) {
                log.warn("Reindex failed for memory {}: {}", m.getId(), e.getMessage());
                skipped++;
            }
        }
        log.info("Reindex complete: generated={}, skipped={}", generated, skipped);
        return Map.of("generated", generated, "skipped", skipped, "total", all.size());
    }
}

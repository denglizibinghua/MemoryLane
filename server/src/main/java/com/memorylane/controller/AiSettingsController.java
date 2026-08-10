package com.memorylane.controller;

import com.memorylane.service.AiSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI provider settings REST API.
 *
 * <p>Configure the active AI provider (OpenAI / Ollama / Anthropic /
 * DashScope / ZhiPu / Moonshot). Changes are persisted to the {@code ai_settings}
 * table and take effect immediately — no restart needed.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    public AiSettingsController(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
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
}

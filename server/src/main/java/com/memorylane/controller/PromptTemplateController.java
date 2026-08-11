package com.memorylane.controller;

import com.memorylane.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Prompt template settings API.
 *
 * <p>GET  /api/v1/settings/prompts     — list all templates with metadata
 * <p>PUT  /api/v1/settings/prompts     — batch-update template contents
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings/prompts")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * Get all prompt templates with metadata (key, name, description, content).
     */
    @GetMapping
    public ResponseEntity<Map<String, Map<String, Object>>> getAll() {
        return ResponseEntity.ok(promptTemplateService.getAllTemplateMeta());
    }

    /**
     * Batch-update prompt template contents.
     *
     * <p>Request body: { "advisor.system": "new content...", "ocr.screenshot.user": "..." }
     * Only provided keys are updated. Unknown keys are silently ignored.
     */
    @PutMapping
    public ResponseEntity<Map<String, String>> update(@RequestBody Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No templates provided"));
        }
        promptTemplateService.updateTemplates(updates);
        log.info("Updated {} prompt template(s)", updates.size());
        return ResponseEntity.ok(promptTemplateService.getAllTemplates());
    }
}

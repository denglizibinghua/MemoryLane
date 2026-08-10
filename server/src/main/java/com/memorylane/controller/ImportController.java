package com.memorylane.controller;

import com.memorylane.adapter.TextPasteAdapter;
import com.memorylane.dto.ImportTextRequest;
import com.memorylane.dto.ImportTextResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final TextPasteAdapter textPasteAdapter;

    /**
     * POST /api/v1/import/text
     *
     * Paste clipboard text — parse, deduplicate, persist.
     * Returns 202 Accepted immediately. Memory extraction runs async (future).
     */
    @PostMapping("/text")
    public ResponseEntity<ImportTextResponse> importText(@Valid @RequestBody ImportTextRequest request) {
        log.info("Received text import: {} chars, platform={}", request.content().length(), request.platform());
        ImportTextResponse response = textPasteAdapter.process(request);
        return ResponseEntity.accepted().body(response);
    }
}

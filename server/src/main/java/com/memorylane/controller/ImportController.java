package com.memorylane.controller;

import com.memorylane.adapter.TextPasteAdapter;
import com.memorylane.dto.ImportTextRequest;
import com.memorylane.dto.ImportTextResponse;
import com.memorylane.entity.Contact;
import com.memorylane.memory.MemoryExtractionService;
import com.memorylane.repository.ContactRepository;
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
    private final MemoryExtractionService extractionService;
    private final ContactRepository contactRepository;

    /**
     * POST /api/v1/import/text
     *
     * Paste clipboard text — parse, deduplicate, persist.
     * Memory extraction is triggered after the transaction commits
     * (avoids async thread reading uncommitted data).
     */
    @PostMapping("/text")
    public ResponseEntity<ImportTextResponse> importText(@Valid @RequestBody ImportTextRequest request) {
        log.info("Received text import: {} chars, platform={}", request.content().length(), request.platform());
        ImportTextResponse response = textPasteAdapter.process(request);

        // Trigger memory extraction AFTER the import transaction commits
        if (response.messageIds() != null && !response.messageIds().isEmpty() && response.contactId() != null) {
            Contact contact = contactRepository.findById(response.contactId()).orElse(null);
            if (contact != null) {
                extractionService.extractAsync(contact, response.messageIds());
            }
        }

        return ResponseEntity.accepted().body(response);
    }
}

package com.memorylane.controller;

import com.memorylane.entity.Memory;
import com.memorylane.entity.MemoryCategory;
import com.memorylane.entity.Message;
import com.memorylane.repository.MemoryRepository;
import com.memorylane.repository.MessageRepository;
import com.memorylane.retrieval.SearchResult;
import com.memorylane.retrieval.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryRepository memoryRepository;
    private final SearchService searchService;
    private final MessageRepository messageRepository;

    @GetMapping("/contact/{contactId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Memory>> listByContact(@PathVariable Long contactId) {
        return ResponseEntity.ok(
                memoryRepository.findByContactIdAndValidUntilIsNullOrderByConfidenceDesc(contactId));
    }

    @GetMapping("/contact/{contactId}/category/{category}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Memory>> listByCategory(
            @PathVariable Long contactId,
            @PathVariable String category) {
        try {
            MemoryCategory cat = MemoryCategory.valueOf(category.toLowerCase());
            return ResponseEntity.ok(
                    memoryRepository.findByContactIdAndCategoryAndValidUntilIsNull(contactId, cat.name().toLowerCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResult>> search(
            @RequestParam String q,
            @RequestParam(required = false) Long contactId) {
        return ResponseEntity.ok(searchService.keywordSearch(q, contactId));
    }

    @GetMapping("/{id}/sources")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageSource>> getSources(@PathVariable Long id) {
        return memoryRepository.findById(id)
                .map(memory -> {
                    Long[] ids = memory.getSourceMsgIds();
                    if (ids == null || ids.length == 0) return List.<MessageSource>of();
                    return messageRepository.findByIdInOrderByRawTimeAsc(Arrays.asList(ids))
                            .stream()
                            .map(m -> new MessageSource(
                                    m.getId(), m.getSpeaker(), m.getContent(), m.getRawTime(),
                                    m.getConversation().getId()))
                            .toList();
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Memory> getById(@PathVariable Long id) {
        return memoryRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record MessageSource(Long id, String speaker, String content, Instant rawTime, Long conversationId) {}
}

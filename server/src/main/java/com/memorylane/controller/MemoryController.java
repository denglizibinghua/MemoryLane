package com.memorylane.controller;

import com.memorylane.entity.Memory;
import com.memorylane.entity.MemoryCategory;
import com.memorylane.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryRepository memoryRepository;

    @GetMapping("/contact/{contactId}")
    public ResponseEntity<List<Memory>> listByContact(@PathVariable Long contactId) {
        return ResponseEntity.ok(
                memoryRepository.findByContactIdAndValidUntilIsNullOrderByConfidenceDesc(contactId));
    }

    @GetMapping("/contact/{contactId}/category/{category}")
    public ResponseEntity<List<Memory>> listByCategory(
            @PathVariable Long contactId,
            @PathVariable String category) {
        try {
            MemoryCategory cat = MemoryCategory.valueOf(category.toLowerCase());
            return ResponseEntity.ok(
                    memoryRepository.findByContactIdAndCategoryAndValidUntilIsNull(contactId, cat));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Memory> getById(@PathVariable Long id) {
        return memoryRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

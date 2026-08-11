package com.memorylane.controller;

import com.memorylane.entity.Contact;
import com.memorylane.repository.ContactRepository;
import com.memorylane.service.ContactService;
import com.memorylane.service.ContactService.DuplicateGroup;
import com.memorylane.service.ContactService.MergeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactRepository contactRepository;
    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<List<Contact>> listAll() {
        return ResponseEntity.ok(contactRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Contact> getById(@PathVariable Long id) {
        return contactRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Contact>> search(@RequestParam String q) {
        return ResponseEntity.ok(contactRepository.findByNameContainingIgnoreCase(q));
    }

    @PostMapping
    public ResponseEntity<Contact> create(@RequestBody Contact contact) {
        return ResponseEntity.ok(contactRepository.save(contact));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contactService.deleteWithCascade(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merge")
    public ResponseEntity<MergeResult> merge(@RequestBody MergeRequest request) {
        return ResponseEntity.ok(contactService.merge(request.targetId(), request.sourceIds()));
    }

    @GetMapping("/duplicates")
    public ResponseEntity<List<DuplicateGroup>> findDuplicates() {
        return ResponseEntity.ok(contactService.findDuplicates());
    }

    public record MergeRequest(Long targetId, List<Long> sourceIds) {}
}

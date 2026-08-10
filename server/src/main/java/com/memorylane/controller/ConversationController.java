package com.memorylane.controller;

import com.memorylane.entity.Conversation;
import com.memorylane.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationRepository conversationRepository;

    @GetMapping("/contact/{contactId}")
    public ResponseEntity<List<Conversation>> listByContact(@PathVariable Long contactId) {
        return ResponseEntity.ok(
                conversationRepository.findByContactIdOrderByLastMsgAtDesc(contactId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Conversation> getById(@PathVariable Long id) {
        return conversationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

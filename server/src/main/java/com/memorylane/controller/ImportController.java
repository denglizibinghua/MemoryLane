package com.memorylane.controller;

import com.memorylane.adapter.TextPasteAdapter;
import com.memorylane.adapter.model.RawMessage;
import com.memorylane.dto.ImportTextRequest;
import com.memorylane.dto.ImportTextResponse;
import com.memorylane.dto.ImportTextResponse.ContactResult;
import com.memorylane.dto.SaveConversationRequest;
import com.memorylane.entity.Contact;
import com.memorylane.entity.Conversation;
import com.memorylane.entity.Message;
import com.memorylane.memory.MemoryExtractionService;
import com.memorylane.repository.ContactRepository;
import com.memorylane.repository.ConversationRepository;
import com.memorylane.repository.MessageRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final TextPasteAdapter textPasteAdapter;
    private final MemoryExtractionService extractionService;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * POST /api/v1/import/preview
     *
     * Parse text without persisting — returns detected platform and unique speakers
     * so the frontend can ask "who are you?" before the real import.
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@Valid @RequestBody ImportTextRequest request) {
        List<RawMessage> parsed = textPasteAdapter.parse(
                request.content(),
                request.contactName(),
                request.platform()
        );

        // Determine platform from the first parsed message, fallback to request
        String platform = parsed.isEmpty() ? request.platform()
                : (parsed.get(0).platform() != null ? parsed.get(0).platform() : request.platform());

        Set<String> speakers = new LinkedHashSet<>();
        for (RawMessage rm : parsed) {
            if (rm.speaker() != null && !rm.speaker().isBlank() && !"self".equals(rm.speaker())) {
                speakers.add(rm.speaker().trim());
            }
        }

        return ResponseEntity.ok(Map.of(
                "platform", platform,
                "speakers", speakers,
                "messageCount", parsed.size()
        ));
    }

    /**
     * POST /api/v1/import/text
     *
     * Full import: parse → distribute to contacts → persist.
     * Memory extraction is triggered per contact after the transaction commits.
     */
    @PostMapping("/text")
    public ResponseEntity<ImportTextResponse> importText(@Valid @RequestBody ImportTextRequest request) {
        log.info("Received text import: {} chars, platform={}, selfName={}",
                request.content().length(), request.platform(), request.selfName());

        ImportTextResponse response = textPasteAdapter.process(request);

        // Trigger memory extraction for each affected contact
        if (response.contacts() != null) {
            for (ContactResult cr : response.contacts()) {
                if (cr.messageIds() != null && !cr.messageIds().isEmpty()) {
                    Contact contact = contactRepository.findById(cr.contactId()).orElse(null);
                    if (contact != null) {
                        log.info("Triggering memory extraction for contact '{}' ({} messages)",
                                contact.getName(), cr.messageIds().size());
                        extractionService.extractAsync(contact, cr.messageIds());
                    }
                }
            }
        }

        return ResponseEntity.accepted().body(response);
    }

    /**
     * POST /api/v1/import/conversation
     *
     * Saves a simulated conversation from the Advisor chat simulator.
     * Messages are already structured speaker:content pairs — no parsing needed.
     * Wrapped in a transaction for atomicity; memory extraction fires after commit.
     */
    @PostMapping("/conversation")
    @Transactional
    public ResponseEntity<?> saveConversation(
            @Valid @RequestBody SaveConversationRequest request) {
        Contact contact = contactRepository.findById(request.contactId()).orElse(null);
        if (contact == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "联系人不存在"));
        }

        String taskId = UUID.randomUUID().toString();
        log.info("[import:{}] Conversation save: contactId={}, {} messages",
                taskId, request.contactId(), request.messages().size());

        Conversation conv = conversationRepository
                .findByContactIdAndPlatform(contact.getId(), "simulated")
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder().contact(contact).platform("simulated").build()));

        int newCount = 0;
        int dupCount = 0;
        List<Long> msgIds = new ArrayList<>();
        Instant firstTime = null;
        Instant lastTime = null;
        Instant now = Instant.now();

        for (SaveConversationRequest.ConversationMessage cm : request.messages()) {
            String hash = sha256(cm.speaker() + "|" + cm.content());
            if (messageRepository.existsByContentHash(hash)) {
                dupCount++;
                continue;
            }

            Instant msgTime = cm.rawTime() != null ? cm.rawTime() : now;
            Message msg = Message.builder()
                    .conversation(conv)
                    .speaker(cm.speaker())
                    .content(cm.content())
                    .rawTime(msgTime)
                    .contentHash(hash)
                    .build();
            msg = messageRepository.save(msg);
            msgIds.add(msg.getId());
            newCount++;

            if (firstTime == null || msgTime.isBefore(firstTime)) firstTime = msgTime;
            if (lastTime == null || msgTime.isAfter(lastTime)) lastTime = msgTime;
        }

        conv.setMessageCount(conv.getMessageCount() + newCount);
        if (firstTime != null && (conv.getFirstMsgAt() == null || firstTime.isBefore(conv.getFirstMsgAt()))) {
            conv.setFirstMsgAt(firstTime);
        }
        if (lastTime != null && (conv.getLastMsgAt() == null || lastTime.isAfter(conv.getLastMsgAt()))) {
            conv.setLastMsgAt(lastTime);
        }
        conversationRepository.save(conv);

        log.info("[import:{}] Conversation saved: {} new, {} dup", taskId, newCount, dupCount);

        ImportTextResponse response = new ImportTextResponse(taskId,
                new ImportTextResponse.ImportStats(newCount, dupCount, 0),
                List.of(new ContactResult(contact.getId(), contact.getName(), newCount, msgIds)));

        // Trigger memory extraction only after the transaction commits,
        // so the async thread sees all persisted messages.
        if (!msgIds.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    extractionService.extractAsync(contact, msgIds);
                }
            });
        }

        return ResponseEntity.accepted().body(response);
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

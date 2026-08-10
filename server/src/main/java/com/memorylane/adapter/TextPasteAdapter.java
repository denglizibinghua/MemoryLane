package com.memorylane.adapter;

import com.memorylane.adapter.model.RawMessage;
import com.memorylane.dto.ImportTextRequest;
import com.memorylane.dto.ImportTextResponse;
import com.memorylane.entity.Contact;
import com.memorylane.entity.Conversation;
import com.memorylane.entity.Message;
import com.memorylane.parser.MessageParser;
import com.memorylane.repository.ContactRepository;
import com.memorylane.repository.ConversationRepository;
import com.memorylane.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * v1 text input adapter — parses clipboard text and persists to database.
 *
 * Pipeline:
 *   1. MessageParser (PlatformDetector → SpeakerExtractor → MessageNormalizer)
 *   2. Find or create Contact
 *   3. Find or create Conversation
 *   4. Batch insert Messages (skip duplicates by content_hash)
 *   5. Trigger async memory extraction via MemoryExtractionService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextPasteAdapter implements InputAdapter {

    private final MessageParser messageParser;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final com.memorylane.memory.MemoryExtractionService extractionService;

    @Override
    public List<RawMessage> parse(String rawContent, String contactHint, String platformHint) {
        return messageParser.parse(rawContent, contactHint, platformHint);
    }

    @Transactional
    public ImportTextResponse process(ImportTextRequest request) {
        String taskId = UUID.randomUUID().toString();
        log.info("[import:{}] Starting text import, {} chars, platform={}",
                taskId, request.content().length(), request.platform());

        // Step 1: Parse text → structured messages
        List<RawMessage> parsed = messageParser.parse(
                request.content(),
                request.contactName(),
                request.platform()
        );

        log.info("[import:{}] Parsed {} messages", taskId, parsed.size());

        // Step 2: Find or create Contact
        String contactName = request.contactName();
        final String resolvedName = (contactName != null && !contactName.isBlank())
                ? contactName
                : extractPrimarySpeaker(parsed);

        Contact contact = contactRepository
                .findByNameAndPlatform(resolvedName, request.platform())
                .orElseGet(() -> {
                    Contact c = Contact.builder()
                            .name(resolvedName)
                            .platform(request.platform())
                            .build();
                    return contactRepository.save(c);
                });

        // Step 3: Find or create Conversation
        Conversation conv = conversationRepository
                .findByContactIdAndPlatform(contact.getId(), request.platform())
                .orElseGet(() -> {
                    Conversation c = Conversation.builder()
                            .contact(contact)
                            .platform(request.platform())
                            .build();
                    return conversationRepository.save(c);
                });

        // Step 4: Batch insert messages (skip duplicates)
        int newCount = 0;
        int dupCount = 0;
        List<Long> newMessageIds = new ArrayList<>();
        Instant firstTime = null;
        Instant lastTime = null;

        for (RawMessage rm : parsed) {
            String hash = sha256(rm.speaker() + "|" + rm.content());
            if (messageRepository.existsByContentHash(hash)) {
                dupCount++;
                continue;
            }

            Message msg = Message.builder()
                    .conversation(conv)
                    .speaker(rm.speaker())
                    .content(rm.content())
                    .rawTime(rm.rawTime())
                    .contentHash(hash)
                    .build();
            msg = messageRepository.save(msg);
            newMessageIds.add(msg.getId());
            newCount++;

            if (rm.rawTime() != null) {
                if (firstTime == null || rm.rawTime().isBefore(firstTime)) {
                    firstTime = rm.rawTime();
                }
                if (lastTime == null || rm.rawTime().isAfter(lastTime)) {
                    lastTime = rm.rawTime();
                }
            }
        }

        // Update conversation stats
        conv.setMessageCount(conv.getMessageCount() + newCount);
        if (firstTime != null && (conv.getFirstMsgAt() == null || firstTime.isBefore(conv.getFirstMsgAt()))) {
            conv.setFirstMsgAt(firstTime);
        }
        if (lastTime != null && (conv.getLastMsgAt() == null || lastTime.isAfter(conv.getLastMsgAt()))) {
            conv.setLastMsgAt(lastTime);
        }
        conversationRepository.save(conv);

        log.info("[import:{}] Done: {} new, {} duplicates, contact={}",
                taskId, newCount, dupCount, contact.getName());

        // Trigger async memory extraction
        if (!newMessageIds.isEmpty()) {
            extractionService.extractAsync(contact, newMessageIds);
        }

        return new ImportTextResponse(
                taskId,
                new ImportTextResponse.ImportStats(newCount, dupCount, 0) // memoriesExtracted = 0 for now (async)
        );
    }

    /**
     * Determine the primary speaker name from parsed messages.
     * Returns the most frequent non-"我" speaker.
     */
    private String extractPrimarySpeaker(List<RawMessage> messages) {
        return messages.stream()
                .map(RawMessage::speaker)
                .filter(s -> !"我".equals(s))
                .findFirst()
                .orElse("Unknown");
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

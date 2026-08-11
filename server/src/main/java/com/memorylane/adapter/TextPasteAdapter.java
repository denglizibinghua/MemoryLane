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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * v1 text input adapter — parses clipboard text and persists to database.
 *
 * <h3>Multi-contact mode (selfName provided)</h3>
 * <ol>
 *   <li>Parse all messages, map {@code selfName} speaker → {@code "self"}</li>
 *   <li>For each unique non-self speaker: create contact + conversation</li>
 *   <li>Self messages are duplicated across all non-self conversations
 *       so each contact's memory extraction sees full context</li>
 * </ol>
 *
 * <h3>Legacy single-contact mode (selfName blank)</h3>
 * <p>Falls back to the old behavior: all messages go to one contact/conversation.</p>
 */
@Slf4j
@Component
public class TextPasteAdapter implements InputAdapter {

    private static final String SELF_SPEAKER = "self";

    private final MessageParser messageParser;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public TextPasteAdapter(MessageParser messageParser,
                            ContactRepository contactRepository,
                            ConversationRepository conversationRepository,
                            MessageRepository messageRepository) {
        this.messageParser = messageParser;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public List<RawMessage> parse(String rawContent, String contactHint, String platformHint) {
        return messageParser.parse(rawContent, contactHint, platformHint);
    }

    @Transactional
    public ImportTextResponse process(ImportTextRequest request) {
        String taskId = UUID.randomUUID().toString();
        log.info("[import:{}] Text import, {} chars, platform={}, selfName={}",
                taskId, request.content().length(), request.platform(), request.selfName());

        // Step 1: Parse
        List<RawMessage> parsed = messageParser.parse(
                request.content(),
                request.contactName(),
                request.platform()
        );
        log.info("[import:{}] Parsed {} messages", taskId, parsed.size());

        String selfName = effectiveSelfName(request);

        if (selfName != null && !selfName.isBlank()) {
            return processMultiContact(taskId, parsed, selfName, request.platform());
        }
        return processLegacy(taskId, parsed, request);
    }

    // ── Multi-contact mode ───────────────────────────────────────────

    private ImportTextResponse processMultiContact(String taskId, List<RawMessage> parsed,
                                                    String selfName, String platform) {
        // Tag self messages
        List<TaggedMessage> tagged = new ArrayList<>();
        Set<String> otherSpeakers = new LinkedHashSet<>();
        for (RawMessage rm : parsed) {
            if (rm.speaker() == null || rm.speaker().isBlank()) continue;
            if (rm.speaker().equals(selfName) || rm.speaker().equals("我")) {
                tagged.add(new TaggedMessage(SELF_SPEAKER, rm.content(), rm.rawTime(), null));
            } else {
                String sp = rm.speaker().trim();
                tagged.add(new TaggedMessage(sp, rm.content(), rm.rawTime(), sp));
                otherSpeakers.add(sp);
            }
        }

        if (otherSpeakers.isEmpty()) {
            log.warn("[import:{}] No non-self speakers found, falling back to legacy", taskId);
            return processLegacy(taskId, parsed, new ImportTextRequest(null, selfName, platform, ""));
        }

        int totalNew = 0;
        int totalDup = 0;
        List<ImportTextResponse.ContactResult> contactResults = new ArrayList<>();

        for (String otherName : otherSpeakers) {
            // Find or create contact
            Contact contact = contactRepository.findByNameAndPlatform(otherName, platform)
                    .orElseGet(() -> contactRepository.save(
                            Contact.builder().name(otherName).platform(platform).build()));

            // Find or create conversation
            Conversation conv = conversationRepository
                    .findByContactIdAndPlatform(contact.getId(), platform)
                    .orElseGet(() -> conversationRepository.save(
                            Conversation.builder().contact(contact).platform(platform).build()));

            // Collect messages for this contact: their own + self
            List<TaggedMessage> contactMsgs = new ArrayList<>();
            for (TaggedMessage tm : tagged) {
                if (tm.contactName == null) {
                    // Self message → goes to ALL non-self conversations
                    contactMsgs.add(tm);
                } else if (tm.contactName.equals(otherName)) {
                    contactMsgs.add(tm);
                }
            }

            // Insert messages
            int newCount = 0;
            int dupCount = 0;
            List<Long> msgIds = new ArrayList<>();
            Instant firstTime = null;
            Instant lastTime = null;

            for (TaggedMessage tm : contactMsgs) {
                String hash = sha256(tm.speaker + "|" + tm.content);
                if (messageRepository.existsByContentHash(hash)) {
                    dupCount++;
                    continue;
                }

                Message msg = Message.builder()
                        .conversation(conv)
                        .speaker(tm.speaker)
                        .content(tm.content)
                        .rawTime(tm.rawTime)
                        .contentHash(hash)
                        .build();
                msg = messageRepository.save(msg);
                msgIds.add(msg.getId());
                newCount++;

                if (tm.rawTime != null) {
                    if (firstTime == null || tm.rawTime.isBefore(firstTime)) firstTime = tm.rawTime;
                    if (lastTime == null || tm.rawTime.isAfter(lastTime)) lastTime = tm.rawTime;
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

            totalNew += newCount;
            totalDup += dupCount;
            contactResults.add(new ImportTextResponse.ContactResult(
                    contact.getId(), contact.getName(), newCount, msgIds));

            log.info("[import:{}] Contact '{}': {} new, {} dup", taskId, contact.getName(), newCount, dupCount);
        }

        log.info("[import:{}] Multi-contact done: {} contacts, {} new, {} dup",
                taskId, contactResults.size(), totalNew, totalDup);

        return new ImportTextResponse(taskId,
                new ImportTextResponse.ImportStats(totalNew, totalDup, 0),
                contactResults);
    }

    // ── Legacy single-contact mode ───────────────────────────────────

    private ImportTextResponse processLegacy(String taskId, List<RawMessage> parsed,
                                              ImportTextRequest request) {
        String contactName = (request.contactName() != null && !request.contactName().isBlank())
                ? request.contactName()
                : extractPrimarySpeaker(parsed);

        Contact contact = contactRepository
                .findByNameAndPlatform(contactName, request.platform())
                .orElseGet(() -> contactRepository.save(
                        Contact.builder().name(contactName).platform(request.platform()).build()));

        Conversation conv = conversationRepository
                .findByContactIdAndPlatform(contact.getId(), request.platform())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder().contact(contact).platform(request.platform()).build()));

        int newCount = 0;
        int dupCount = 0;
        List<Long> msgIds = new ArrayList<>();
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
            msgIds.add(msg.getId());
            newCount++;

            if (rm.rawTime() != null) {
                if (firstTime == null || rm.rawTime().isBefore(firstTime)) firstTime = rm.rawTime();
                if (lastTime == null || rm.rawTime().isAfter(lastTime)) lastTime = rm.rawTime();
            }
        }

        conv.setMessageCount(conv.getMessageCount() + newCount);
        if (firstTime != null && (conv.getFirstMsgAt() == null || firstTime.isBefore(conv.getFirstMsgAt()))) {
            conv.setFirstMsgAt(firstTime);
        }
        if (lastTime != null && (conv.getLastMsgAt() == null || lastTime.isAfter(conv.getLastMsgAt()))) {
            conv.setLastMsgAt(lastTime);
        }
        conversationRepository.save(conv);

        log.info("[import:{}] Legacy done: {} new, {} dup, contact={}", taskId, newCount, dupCount, contact.getName());

        return new ImportTextResponse(taskId,
                new ImportTextResponse.ImportStats(newCount, dupCount, 0),
                List.of(new ImportTextResponse.ContactResult(
                        contact.getId(), contact.getName(), newCount, msgIds)));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Internal representation for the multi-contact distribution loop. */
    private record TaggedMessage(String speaker, String content, Instant rawTime, String contactName) {}

    private String effectiveSelfName(ImportTextRequest request) {
        if (request.selfName() != null && !request.selfName().isBlank()) return request.selfName().trim();
        return null;
    }

    private String extractPrimarySpeaker(List<RawMessage> messages) {
        return messages.stream()
                .map(RawMessage::speaker)
                .filter(s -> !"我".equals(s) && !"self".equals(s))
                .findFirst()
                .orElse("Unknown");
    }

    static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

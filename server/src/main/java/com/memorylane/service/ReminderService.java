package com.memorylane.service;

import com.memorylane.dto.CreateReminderRequest;
import com.memorylane.dto.ReminderDTO;
import com.memorylane.entity.Memory;
import com.memorylane.entity.Reminder;
import com.memorylane.repository.ContactRepository;
import com.memorylane.repository.MemoryRepository;
import com.memorylane.repository.ReminderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core reminder business logic.
 *
 * <p>All public methods that return reminders map to {@link ReminderDTO}
 * inside the transaction boundary so that lazy associations resolve safely
 * — no {@code LazyInitializationException} in the controller layer.
 */
@Slf4j
@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final MemoryRepository memoryRepository;
    private final ContactRepository contactRepository;

    public ReminderService(ReminderRepository reminderRepository,
                           MemoryRepository memoryRepository,
                           ContactRepository contactRepository) {
        this.reminderRepository = reminderRepository;
        this.memoryRepository = memoryRepository;
        this.contactRepository = contactRepository;
    }

    /** Data transfer across the transaction → LLM → transaction boundary. */
    public record PromiseCandidate(long memoryId, String content, String contactName) {}

    @Transactional
    public List<PromiseCandidate> loadCandidates() {
        List<Memory> promises = memoryRepository.findAllPromiseMemories();
        if (promises.isEmpty()) return List.of();

        List<PromiseCandidate> candidates = new ArrayList<>();
        Instant now = Instant.now();
        for (Memory mem : promises) {
            if (reminderRepository.findByMemoryId(mem.getId()).isPresent()) {
                mem.setReminderScannedAt(now);
                memoryRepository.save(mem);
                continue;
            }
            String contactName = mem.getContact() != null ? mem.getContact().getName() : "对方";
            candidates.add(new PromiseCandidate(mem.getId(), mem.getContent(), contactName));
            mem.setReminderScannedAt(now);
            memoryRepository.save(mem);
        }
        log.debug("loadCandidates — {} candidates", candidates.size());
        return candidates;
    }

    @Transactional
    public ReminderDTO createReminder(long memoryId, TimeExpressionParserService.ParsedTime parsed) {
        Memory mem = memoryRepository.findById(memoryId).orElse(null);
        if (mem == null) return null;

        Instant remindAt = parsed.remindAt();
        if (remindAt == null) {
            remindAt = parsed.eventTime().minusSeconds(30 * 60);
        }
        if (remindAt.isBefore(Instant.now())) {
            log.info("createReminder — skipping past reminder for memory={}", memoryId);
            return null;
        }

        Reminder reminder = Reminder.builder()
                .memory(mem)
                .contact(mem.getContact())
                .title(parsed.title() != null && !parsed.title().isBlank()
                        ? parsed.title() : "约定的提醒")
                .eventTime(parsed.eventTime())
                .remindAt(remindAt)
                .sourceText(parsed.sourceText())
                .status("confirmed")
                .build();

        try {
            Reminder saved = reminderRepository.save(reminder);
            log.info("Reminder created: id={}, title={}, remindAt={}", saved.getId(),
                    saved.getTitle(), saved.getRemindAt());
            return ReminderDTO.from(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("createReminder — duplicate for memory={}, skipping", memoryId);
            return null;
        }
    }

    @Transactional
    public List<ReminderDTO> triggerDueReminders() {
        List<Reminder> due = reminderRepository.findByStatusAndRemindAtBefore("confirmed", Instant.now());
        for (Reminder r : due) {
            r.setStatus("triggered");
        }
        reminderRepository.saveAll(due);
        if (!due.isEmpty()) {
            log.info("triggerDueReminders — triggered {} reminder(s)", due.size());
        }
        return due.stream().map(ReminderDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReminderDTO> getReminders(Long contactId, String status) {
        List<Reminder> reminders;
        if (contactId != null && status != null) {
            reminders = reminderRepository.findByContactIdAndStatus(contactId, status);
        } else if (contactId != null) {
            reminders = reminderRepository.findByContactIdOrderByRemindAtDesc(contactId);
        } else if (status != null) {
            reminders = reminderRepository.findByStatusOrderByRemindAtAsc(status);
        } else {
            reminders = new ArrayList<>(reminderRepository.findByStatusOrderByRemindAtAsc("confirmed"));
            reminders.addAll(reminderRepository.findByStatusOrderByRemindAtAsc("triggered"));
        }
        return reminders.stream().map(ReminderDTO::from).toList();
    }

    @Transactional
    public Optional<ReminderDTO> confirmReminder(Long id) {
        return reminderRepository.findById(id).map(r -> {
            if ("triggered".equals(r.getStatus())) {
                log.warn("confirmReminder — id={} already triggered, ignoring", id);
                return ReminderDTO.from(r);
            }
            r.setStatus("confirmed");
            return ReminderDTO.from(reminderRepository.save(r));
        });
    }

    @Transactional
    public Optional<ReminderDTO> dismissReminder(Long id) {
        return reminderRepository.findById(id).map(r -> {
            r.setStatus("dismissed");
            return ReminderDTO.from(reminderRepository.save(r));
        });
    }

    @Transactional
    public ReminderDTO createManual(CreateReminderRequest req) {
        var contact = contactRepository.findById(req.contactId())
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + req.contactId()));
        Instant remindAt = req.remindAt() != null ? req.remindAt() : req.eventTime().minusSeconds(30 * 60);

        Reminder reminder = Reminder.builder()
                .contact(contact)
                .title(req.title())
                .eventTime(req.eventTime())
                .remindAt(remindAt)
                .status("confirmed")
                .build();
        Reminder saved = reminderRepository.save(reminder);
        log.info("Manual reminder created: id={}, title={}", saved.getId(), saved.getTitle());
        return ReminderDTO.from(saved);
    }

    @Transactional
    public Optional<ReminderDTO> updateReminder(Long id, CreateReminderRequest req) {
        return reminderRepository.findById(id).map(r -> {
            r.setTitle(req.title());
            r.setEventTime(req.eventTime());
            r.setRemindAt(req.remindAt() != null ? req.remindAt() : req.eventTime().minusSeconds(30 * 60));
            return ReminderDTO.from(reminderRepository.save(r));
        });
    }

    @Transactional
    public void resetScannedAt(long memoryId) {
        memoryRepository.findById(memoryId).ifPresent(mem -> {
            mem.setReminderScannedAt(null);
            memoryRepository.save(mem);
            log.debug("resetScannedAt — memory={} ready for retry", memoryId);
        });
    }

    public long countByStatus(String status) {
        return reminderRepository.countByStatus(status);
    }
}

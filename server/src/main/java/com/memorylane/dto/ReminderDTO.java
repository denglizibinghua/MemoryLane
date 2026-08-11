package com.memorylane.dto;

import com.memorylane.entity.Reminder;

import java.time.Instant;

/**
 * Serializable projection of {@link Reminder} that eagerly resolves
 * LAZY associations inside the transaction boundary — avoids
 * {@code LazyInitializationException} when Jackson serializes outside a
 * transaction (or when {@code open-in-view} is disabled).
 */
public record ReminderDTO(
        long id,
        String title,
        Instant remindAt,
        Instant eventTime,
        String status,
        String sourceText,
        Instant createdAt,
        ContactRef contact,
        MemoryRef memory
) {
    public record ContactRef(long id, String name) {}
    public record MemoryRef(long id) {}

    /** Map from entity inside a transaction (safe to access lazy fields). */
    public static ReminderDTO from(Reminder r) {
        ContactRef contact = r.getContact() != null
                ? new ContactRef(r.getContact().getId(), r.getContact().getName())
                : null;
        MemoryRef memory = r.getMemory() != null
                ? new MemoryRef(r.getMemory().getId())
                : null;
        return new ReminderDTO(
                r.getId(),
                r.getTitle(),
                r.getRemindAt(),
                r.getEventTime(),
                r.getStatus(),
                r.getSourceText(),
                r.getCreatedAt(),
                contact,
                memory
        );
    }
}

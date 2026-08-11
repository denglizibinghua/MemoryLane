package com.memorylane.dto;

import java.util.List;

/**
 * Response from text import — returned immediately (202 Accepted).
 * Memory extraction runs asynchronously per contact.
 */
public record ImportTextResponse(
        String taskId,
        ImportStats stats,
        List<ContactResult> contacts
) {
    public record ImportStats(
            int newMessages,
            int duplicates,
            int memoriesExtracted
    ) {}

    public record ContactResult(
            Long contactId,
            String contactName,
            int messageCount,
            List<Long> messageIds
    ) {}
}

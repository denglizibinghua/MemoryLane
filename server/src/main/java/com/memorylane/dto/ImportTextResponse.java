package com.memorylane.dto;

import java.util.List;

/**
 * Response from text import endpoint — returned immediately (202 Accepted).
 * Memory extraction runs asynchronously — {@code contactId} and
 * {@code messageIds} are consumed by the controller to trigger extraction
 * after the transaction commits.
 */
public record ImportTextResponse(
        String taskId,
        ImportStats stats,
        Long contactId,
        List<Long> messageIds
) {
    public record ImportStats(
            int newMessages,
            int duplicates,
            int memoriesExtracted
    ) {}
}

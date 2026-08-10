package com.memorylane.dto;

/**
 * Response from text import endpoint — returned immediately (202 Accepted).
 * Memory extraction runs asynchronously.
 */
public record ImportTextResponse(
        String taskId,
        ImportStats stats
) {
    public record ImportStats(
            int newMessages,
            int duplicates,
            int memoriesExtracted
    ) {}
}

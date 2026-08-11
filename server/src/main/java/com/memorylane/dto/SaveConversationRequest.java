package com.memorylane.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request body for POST /api/v1/import/conversation.
 *
 * <p>Saves an entire simulated conversation (from the Advisor chat simulator)
 * as structured messages — no message parsing needed.
 */
public record SaveConversationRequest(
        @NotNull Long contactId,
        @Valid @NotNull @Size(min = 1) List<ConversationMessage> messages
) {
    public record ConversationMessage(
            @NotNull String speaker,   // "self" | contact name
            @NotNull String content,
            Instant rawTime             // nullable, defaults to now
    ) {}
}

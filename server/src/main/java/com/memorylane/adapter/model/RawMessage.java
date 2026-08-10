package com.memorylane.adapter.model;

import java.time.Instant;

/**
 * Parsed raw message from import pipeline — intermediate representation
 * between text input and database entities.
 */
public record RawMessage(
        String speaker,
        String content,
        Instant rawTime,
        String platform,
        String contentHash
) {}

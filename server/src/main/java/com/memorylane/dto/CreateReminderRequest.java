package com.memorylane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Request body for manual reminder creation / update. */
public record CreateReminderRequest(
        @NotNull Long contactId,
        @NotBlank String title,
        @NotNull Instant eventTime,
        Instant remindAt  // defaults to eventTime - 30 min if null
) {}

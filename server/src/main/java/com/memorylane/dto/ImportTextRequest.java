package com.memorylane.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for text import endpoint.
 */
public record ImportTextRequest(
        String contactName,       // optional — AI auto-detect if blank
        @NotBlank String platform, // "auto" | "wechat" | "qq" | "douyin" | "sms" | "generic"
        @NotBlank String content   // raw clipboard text
) {}

package com.memorylane.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for text import endpoint.
 *
 * @param selfName  "我是谁" — the speaker name that maps to 'self'.
 *                  When provided, all other speakers get their own contacts.
 * @param contactName fallback when selfName is blank (legacy single-contact mode).
 * @param platform  "auto" | "wechat" | "qq" | "douyin" | "sms" | "generic"
 * @param content   raw clipboard text
 */
public record ImportTextRequest(
        String selfName,
        String contactName,
        @NotBlank String platform,
        @NotBlank String content
) {}

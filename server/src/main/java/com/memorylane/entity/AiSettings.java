package com.memorylane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI provider settings persisted in the {@code ai_settings} table.
 *
 * <p>A single row (id=1) holds the active provider configuration. Changes take
 * effect immediately — the running ChatModel is rebuilt from these values, so
 * no restart is required.
 */
@Entity
@Table(name = "ai_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSettings {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "api_key", length = 512)
    private String apiKey;

    @Column(name = "api_base", length = 512)
    private String apiBase;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.memorylane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User-editable AI prompt template.
 *
 * <p>Each row maps a logical key (e.g. "advisor.system") to the full
 * prompt text sent to the LLM. Built-in templates are seeded by V10
 * migration; users can edit {@link #content} through the settings UI.
 *
 * <p>Placeholder syntax: {@code {placeholderName}} — consumers do simple
 * string replacement to avoid {@code String.format} issues when content
 * contains literal '%' characters.
 */
@Entity
@Table(name = "prompt_templates")
@Data
public class PromptTemplate {

    @Id
    @Column(length = 64)
    private String key;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 256)
    private String description;

    @Column(nullable = false)
    private boolean isBuiltin;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

package com.memorylane.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "user_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String persona;

    @Column(name = "speaking_style", length = 100)
    private String speakingStyle;

    @Column(name = "relationship_default", length = 200)
    private String relationshipDefault;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_suggested", columnDefinition = "JSONB DEFAULT '{}'")
    private String aiSuggested;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

package com.memorylane.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "messages",
       uniqueConstraints = @UniqueConstraint(name = "idx_messages_hash", columnNames = "contentHash"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false, length = 50)
    private String speaker;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "raw_time")
    private Instant rawTime;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private Integer importance = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}

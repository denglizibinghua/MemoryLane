package com.memorylane.repository;

import com.memorylane.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByRawTimeDesc(Long conversationId, Pageable pageable);

    boolean existsByContentHash(String contentHash);

    /** Per-conversation dedup (V7+): checks hash within a single conversation only. */
    boolean existsByConversationIdAndContentHash(Long conversationId, String contentHash);

    List<Message> findByConversationIdAndImportanceGreaterThanEqual(Long conversationId, Integer minImportance);

    List<Message> findByIdInOrderByRawTimeAsc(List<Long> ids);

    void deleteByConversationIdIn(List<Long> conversationIds);
}

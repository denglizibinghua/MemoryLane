package com.memorylane.repository;

import com.memorylane.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByContactIdOrderByLastMsgAtDesc(Long contactId);

    Optional<Conversation> findByContactIdAndPlatform(Long contactId, String platform);

    void deleteByContactId(Long contactId);
}

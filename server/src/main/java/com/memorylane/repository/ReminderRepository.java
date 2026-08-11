package com.memorylane.repository;

import com.memorylane.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByContactIdOrderByRemindAtDesc(Long contactId);

    List<Reminder> findByStatusAndRemindAtBefore(String status, Instant before);

    List<Reminder> findByContactIdAndStatus(Long contactId, String status);

    void deleteByContactId(Long contactId);

    java.util.Optional<Reminder> findByMemoryId(Long memoryId);

    List<Reminder> findByStatusOrderByRemindAtAsc(String status);

    long countByStatus(String status);
}

package com.memorylane.repository;

import com.memorylane.entity.Memory;
import com.memorylane.entity.MemoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {

    List<Memory> findByContactIdAndValidUntilIsNullOrderByConfidenceDesc(Long contactId);

    /** Native query to avoid PostgreSQL enum-vs-varchar type mismatch. */
    @Query(value = "SELECT * FROM memories WHERE contact_id = :contactId AND category::text = :category AND valid_until IS NULL",
           nativeQuery = true)
    List<Memory> findByContactIdAndCategoryAndValidUntilIsNull(@Param("contactId") Long contactId,
                                                                @Param("category") String category);

    List<Memory> findByContactIdAndValidUntilIsNull(Long contactId);

    List<Memory> findByContactId(Long contactId);

    void deleteByContactId(Long contactId);

    /** Find promise memories not yet scanned for reminders. */
    @Query("SELECT m FROM Memory m WHERE m.category = com.memorylane.entity.MemoryCategory.promise AND m.validUntil IS NULL AND m.reminderScannedAt IS NULL")
    List<Memory> findAllPromiseMemories();
}

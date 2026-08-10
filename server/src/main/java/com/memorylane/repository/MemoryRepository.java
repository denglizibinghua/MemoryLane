package com.memorylane.repository;

import com.memorylane.entity.Memory;
import com.memorylane.entity.MemoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {

    List<Memory> findByContactIdAndValidUntilIsNullOrderByConfidenceDesc(Long contactId);

    List<Memory> findByContactIdAndCategoryAndValidUntilIsNull(Long contactId, MemoryCategory category);

    List<Memory> findByContactIdAndValidUntilIsNull(Long contactId);
}

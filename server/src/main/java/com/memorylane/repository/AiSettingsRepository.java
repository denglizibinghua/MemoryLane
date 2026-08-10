package com.memorylane.repository;

import com.memorylane.entity.AiSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiSettingsRepository extends JpaRepository<AiSettings, Long> {

    @Query("SELECT a FROM AiSettings a ORDER BY a.id ASC LIMIT 1")
    Optional<AiSettings> findFirst();
}

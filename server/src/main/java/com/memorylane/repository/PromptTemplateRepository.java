package com.memorylane.repository;

import com.memorylane.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Prompt template CRUD. Single-row-per-key design — no user isolation
 * needed (single-user app, same as ai_settings id=1 and user_profile).
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, String> {
}

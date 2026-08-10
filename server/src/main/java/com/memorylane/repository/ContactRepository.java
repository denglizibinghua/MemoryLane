package com.memorylane.repository;

import com.memorylane.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByNameAndPlatform(String name, String platform);

    List<Contact> findByPlatform(String platform);

    List<Contact> findByNameContainingIgnoreCase(String keyword);
}

package com.shoptopus.core.audit.repository;

import com.shoptopus.core.audit.Audit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<Audit, UUID> {

    List<Audit> findAllByOrderByCreatedAtDesc();

    Optional<Audit> findById(UUID id);
}

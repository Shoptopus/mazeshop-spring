package com.shoptopus.core.logging.repository;

import com.shoptopus.core.logging.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {

    List<AuditTrail> findAllByOrderByCreatedAtDesc();
    List<AuditTrail> findByStoreIdOrderByCreatedAtDesc(UUID storeId);
}

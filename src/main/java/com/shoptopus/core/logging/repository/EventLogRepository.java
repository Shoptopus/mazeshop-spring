package com.shoptopus.core.logging.repository;

import com.shoptopus.core.logging.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventLogRepository extends JpaRepository<EventLog, UUID> {
    java.util.List<EventLog> findByStoreIdOrderByCreatedAtDesc(UUID storeId);
}

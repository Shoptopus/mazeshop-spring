package com.shoptopus.core.email.repository;

import com.shoptopus.core.email.OutgoingEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutgoingEmailRepository extends JpaRepository<OutgoingEmail, UUID> {
    List<OutgoingEmail> findByStatusOrderByQueuedAtAsc(String status);
}

package com.shoptopus.core.messaging.repository;

import com.shoptopus.core.messaging.OrderMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, UUID> {
    List<OrderMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}

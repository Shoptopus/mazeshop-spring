package com.shoptopus.core.messaging.repository;

import com.shoptopus.core.messaging.OrderMessageThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderMessageThreadRepository extends JpaRepository<OrderMessageThread, UUID> {
    Optional<OrderMessageThread> findByOrderId(UUID orderId);
    Page<OrderMessageThread> findAllByOrderByLastMessageAtDescCreatedAtDesc(Pageable pageable);
    Page<OrderMessageThread> findByCustomerUserIdOrderByLastMessageAtDescCreatedAtDesc(UUID customerUserId, Pageable pageable);
    Optional<OrderMessageThread> findByIdAndCustomerUserId(UUID id, UUID customerUserId);
}

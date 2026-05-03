package com.shoptopus.core.payment.repository;

import com.shoptopus.core.payment.PaymentSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentSourceRepository extends JpaRepository<PaymentSource, UUID> {
    List<PaymentSource> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
    Optional<PaymentSource> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}

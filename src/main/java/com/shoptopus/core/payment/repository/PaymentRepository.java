package com.shoptopus.core.payment.repository;

import com.shoptopus.core.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByDeletedAtIsNull();
    List<Payment> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID storeId);
    List<Payment> findByPayableTypeAndPayableIdAndDeletedAtIsNull(String payableType, String payableId);
}

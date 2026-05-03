package com.shoptopus.core.invoice.repository;

import com.shoptopus.core.invoice.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Invoice> findBySlugAndDeletedAtIsNull(String slug);

    List<Invoice> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<Invoice> findFirstByOrderIdAndDeletedAtIsNull(UUID orderId);
}

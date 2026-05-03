package com.shoptopus.core.ordering.repository;

import com.shoptopus.core.ordering.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserIdAndDeletedAtIsNull(UUID userId);
    List<Order> findByDeletedAtIsNull();
    List<Order> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Order> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID storeId);
    Optional<Order> findByIdAndDeletedAtIsNull(UUID id);
}

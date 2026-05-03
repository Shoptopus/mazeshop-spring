package com.shoptopus.core.delivery.repository;

import com.shoptopus.core.delivery.DeliveryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryTypeRepository extends JpaRepository<DeliveryType, UUID> {

    List<DeliveryType> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<DeliveryType> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID storeId);

    Optional<DeliveryType> findByIdAndDeletedAtIsNull(UUID id);
}

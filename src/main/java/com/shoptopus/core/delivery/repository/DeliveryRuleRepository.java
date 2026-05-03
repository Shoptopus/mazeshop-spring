package com.shoptopus.core.delivery.repository;

import com.shoptopus.core.delivery.DeliveryRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRuleRepository extends JpaRepository<DeliveryRule, UUID> {

    List<DeliveryRule> findByDeliveryTypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID deliveryTypeId);

    Optional<DeliveryRule> findByIdAndDeletedAtIsNull(UUID id);
}

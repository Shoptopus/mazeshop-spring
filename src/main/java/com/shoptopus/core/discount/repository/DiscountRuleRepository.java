package com.shoptopus.core.discount.repository;

import com.shoptopus.core.discount.DiscountRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountRuleRepository extends JpaRepository<DiscountRule, UUID> {

    List<DiscountRule> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<DiscountRule> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID storeId);

    Optional<DiscountRule> findByIdAndDeletedAtIsNull(UUID id);
}

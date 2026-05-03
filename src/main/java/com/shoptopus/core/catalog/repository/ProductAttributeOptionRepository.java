package com.shoptopus.core.catalog.repository;

import com.shoptopus.core.catalog.ProductAttributeOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAttributeOptionRepository extends JpaRepository<ProductAttributeOption, UUID> {
    List<ProductAttributeOption> findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(UUID productAttributeId);
}

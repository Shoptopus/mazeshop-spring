package com.shoptopus.core.catalog.repository;

import com.shoptopus.core.catalog.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId);

    List<ProductVariant> findByProductIdInAndDeletedAtIsNull(List<UUID> productIds);
}

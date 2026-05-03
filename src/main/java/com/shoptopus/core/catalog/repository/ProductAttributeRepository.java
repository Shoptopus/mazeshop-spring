package com.shoptopus.core.catalog.repository;

import com.shoptopus.core.catalog.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, UUID> {
    List<ProductAttribute> findByDeletedAtIsNullAndEnabledTrue();
}

package com.shoptopus.core.catalog.repository;

import com.shoptopus.core.catalog.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    List<ProductCategory> findByDeletedAtIsNullAndEnabledTrue();
    Optional<ProductCategory> findBySlugAndDeletedAtIsNull(String slug);
}

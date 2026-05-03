package com.shoptopus.core.catalog.repository;

import com.shoptopus.core.catalog.ProductTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductTagRepository extends JpaRepository<ProductTag, UUID> {
    List<ProductTag> findByDeletedAtIsNullAndEnabledTrue();
}

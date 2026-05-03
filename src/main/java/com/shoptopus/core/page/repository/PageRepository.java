package com.shoptopus.core.page.repository;

import com.shoptopus.core.page.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PageRepository extends JpaRepository<Page, UUID> {
    Optional<Page> findBySlugAndDeletedAtIsNull(String slug);
}

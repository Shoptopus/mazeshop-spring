package com.shoptopus.core.page.repository;

import com.shoptopus.core.page.PageMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PageMenuItemRepository extends JpaRepository<PageMenuItem, UUID> {
    Optional<PageMenuItem> findByItemTypeAndStoreId(String itemType, UUID storeId);
    List<PageMenuItem> findByStoreId(UUID storeId);
    void deleteByStoreId(UUID storeId);
}

package com.shoptopus.core.store.repository;

import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    Page<Store> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);
    Page<Store> findByDeletedAtIsNullAndNameContainingIgnoreCaseOrderByCreatedAtDesc(String search, Pageable pageable);
    List<Store> findByDeletedAtIsNullOrderByNameAsc();
    List<Store> findByDeletedAtIsNullAndStatusOrderByNameAsc(StoreStatus status);
    Optional<Store> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Store> findBySlugAndDeletedAtIsNull(String slug);
    Optional<Store> findFirstByDeletedAtIsNullOrderByCreatedAtAsc();
}

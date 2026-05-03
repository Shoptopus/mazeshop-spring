package com.shoptopus.core.voucher.repository;

import com.shoptopus.core.voucher.VoucherCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoucherCodeRepository extends JpaRepository<VoucherCode, UUID> {

    List<VoucherCode> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<VoucherCode> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID storeId);

    Optional<VoucherCode> findByIdAndDeletedAtIsNull(UUID id);

    Optional<VoucherCode> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);
    Optional<VoucherCode> findByCodeIgnoreCaseAndStoreIdAndDeletedAtIsNull(String code, UUID storeId);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);
}

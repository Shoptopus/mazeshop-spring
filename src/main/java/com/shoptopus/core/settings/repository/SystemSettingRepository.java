package com.shoptopus.core.settings.repository;

import com.shoptopus.core.settings.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {
    Optional<SystemSetting> findByKeyAndDeletedAtIsNull(String key);
    java.util.List<SystemSetting> findByDeletedAtIsNullOrderByKeyAsc();
}

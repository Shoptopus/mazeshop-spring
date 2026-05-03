package com.shoptopus.core.engagement.repository;

import com.shoptopus.core.engagement.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BannerRepository extends JpaRepository<Banner, UUID> {
    List<Banner> findByDeletedAtIsNullAndEnabledTrueOrderByCreatedAtDesc();
}

package com.shoptopus.core.publicapi.banner;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.repository.BannerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

abstract class BannerApiTestBase extends IntegrationTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected BannerRepository bannerRepository;

    protected Banner createBanner(boolean enabled, boolean softDeleted, long clickCount) {
        Banner banner = new Banner();
        banner.setName(Map.of("en", "Banner " + UUID.randomUUID()));
        banner.setSlug("banner-" + UUID.randomUUID());
        banner.setImage(Map.of("url", "/img/banner.png"));
        banner.setEnabled(enabled);
        banner.setClickCount(clickCount);
        if (softDeleted) {
            banner.setDeletedAt(OffsetDateTime.now());
        }
        return bannerRepository.save(banner);
    }
}


package com.shoptopus.core.publicapi.banner;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class BannerClickTest extends BannerApiTestBase {

    @Test
    void banner_click_increases_total_click_count() {
        var first = createBanner(true, false, 0);
        var second = createBanner(true, false, 0);

        var res = restTemplate.postForEntity("/api/banners/" + first.getId() + "/click", java.util.Map.of(), java.util.Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var firstReloaded = bannerRepository.findById(first.getId()).orElseThrow();
        var secondReloaded = bannerRepository.findById(second.getId()).orElseThrow();
        assertThat(firstReloaded.getClickCount()).isEqualTo(1);
        assertThat(secondReloaded.getClickCount()).isEqualTo(0);
    }
}


package com.shoptopus.core.publicapi.banner;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GetAllBannersTest extends BannerApiTestBase {

    @Test
    void it_returns_correct_format() {
        var res = restTemplate.getForEntity("/api/banners", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().keySet()).contains("message", "data", "records", "total_records");
    }

    @Test
    void it_returns_all_required_fields() {
        int before = currentEnabledBannerCount();
        createBanner(true, false, 0);
        createBanner(true, false, 1);

        var res = restTemplate.getForEntity("/api/banners", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(data.size()).isGreaterThanOrEqualTo(before + 2);
        assertThat(data.get(0).keySet().stream().map(String::valueOf).toList())
                .contains("id", "name", "slug", "image", "clickCount");
    }

    @Test
    void soft_deleted_and_disabled_banners_are_not_returned() {
        var softDeleted = createBanner(true, true, 0);
        var disabled = createBanner(false, false, 0);

        var res = restTemplate.getForEntity("/api/banners", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (List<Map<String, Object>>) res.getBody().get("data");
        var ids = data.stream().map(row -> String.valueOf(row.get("id"))).toList();
        assertThat(ids).doesNotContain(softDeleted.getId().toString(), disabled.getId().toString());
    }

    @Test
    void it_returns_count() {
        int before = currentEnabledBannerCount();
        createBanner(true, false, 0);
        createBanner(true, false, 0);

        var res = restTemplate.getForEntity("/api/banners", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) res.getBody().get("total_records")).intValue()).isEqualTo(before + 2);
    }

    private int currentEnabledBannerCount() {
        var res = restTemplate.getForEntity("/api/banners", Map.class);
        @SuppressWarnings("unchecked")
        var data = (List<Map<String, Object>>) res.getBody().get("data");
        return data.size();
    }
}

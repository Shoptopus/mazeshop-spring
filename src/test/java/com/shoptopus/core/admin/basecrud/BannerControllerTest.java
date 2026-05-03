package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.repository.BannerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BannerControllerTest extends AdminControllerTestBase {

    @Autowired
    private BannerRepository bannerRepository;

    @Test
    void test_banner_can_be_created_updated_and_deleted() {
        String token = token();

        var create = authed(token, HttpMethod.POST, "/api/admin/banner", Map.of(
                "name", Map.of("en", "Hero"),
                "slug", "banner-" + UUID.randomUUID()
        ));
        assert2xx(create);
        UUID id = UUID.fromString(((Map<?, ?>) create.getBody().get("data")).get("id").toString());

        var list = authed(token, HttpMethod.GET, "/api/admin/banners", null);
        assert2xx(list);
        Map<?, ?> listed = ((java.util.List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> id.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(listed.get("name")).isEqualTo(Map.of("en", "Hero"));

        var show = authed(token, HttpMethod.GET, "/api/admin/banner/" + id, null);
        assert2xx(show);
        Map<?, ?> showData = (Map<?, ?>) show.getBody().get("data");
        assertThat(showData.get("id")).isEqualTo(id.toString());
        assertThat(showData.get("name")).isEqualTo(Map.of("en", "Hero"));

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/banner/" + id, Map.of("enabled", false)));
        Banner banner = bannerRepository.findById(id).orElseThrow();
        assertThat(banner.isEnabled()).isFalse();

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/banner/" + id, null));
        banner = bannerRepository.findById(id).orElseThrow();
        assertThat(banner.getDeletedAt()).isNotNull();
    }

    @Test
    void test_banner_creation_validation() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/banner", Map.of("enabled", true));
        assertValidationFailed(response, "name field is required");
    }

    @Test
    void test_banner_requires_banner_manage_permission() {
        String email = "banner-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Banner",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "marketing_manager");
        assignPermissionToRole("marketing_manager", "banners.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/banners", null).getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/banner", Map.of("name", Map.of("en", "Allowed"))).getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
    }
}

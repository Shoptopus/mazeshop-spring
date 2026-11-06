package com.shoptopus.core.storefront;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.page.Page;
import com.shoptopus.core.page.repository.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.i18n.languages=en,de",
        "app.storefront.social-providers=google,facebook"
})
class StorefrontBootstrapApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private com.shoptopus.core.social.SocialProviderSettingsService socialProviders;

    @LocalServerPort
    private int port;

    @Test
    void storefront_bootstrap_endpoints_are_public_and_return_expected_shape() {
        // Persisted settings override properties; do not depend on earlier tests' changes.
        socialProviders.saveAdminConfigs(java.util.List.of(
                Map.of("provider", "google", "enabled", true),
                Map.of("provider", "facebook", "enabled", true)));
        Page page = new Page();
        page.setTitle(Map.of("en", "Home", "de", "Start"));
        page.setSlug("home-" + UUID.randomUUID());
        page.setStatus(1);
        page.setShowInMenu(true);
        page.setHomePage(true);
        pageRepository.save(page);

        var config = exchange(HttpMethod.GET, "/api/storefront/config");
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        var configData = dataObject(config);
        assertThat(configData).containsKeys("auth", "i18n", "media", "commerce");

        var auth = (Map<?, ?>) configData.get("auth");
        assertThat(auth.get("social_providers")).isEqualTo(java.util.List.of("google", "facebook"));

        var home = exchange(HttpMethod.GET, "/api/storefront/home");
        assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);
        var homeData = dataObject(home);
        assertThat(homeData).containsKeys("banners", "menu_pages", "menu_tree", "home_page");
        assertThat((java.util.List<?>) homeData.get("menu_tree")).isNotEmpty();

        var pages = exchange(HttpMethod.GET, "/api/storefront/pages");
        assertThat(pages.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataList(pages))
                .extracting(row -> row.get("slug"))
                .contains(page.getSlug());

        var pageBySlug = exchange(HttpMethod.GET, "/api/storefront/pages/" + page.getSlug());
        assertThat(pageBySlug.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataObject(pageBySlug).get("slug")).isEqualTo(page.getSlug());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataObject(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> dataList(ResponseEntity<Map> response) {
        return (java.util.List<Map<String, Object>>) response.getBody().get("data");
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path) {
        return restTemplate.exchange("http://127.0.0.1:" + port + path, method, HttpEntity.EMPTY, Map.class);
    }
}

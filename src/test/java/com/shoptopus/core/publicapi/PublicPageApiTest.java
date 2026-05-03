package com.shoptopus.core.publicapi;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.content.Content;
import com.shoptopus.core.content.repository.ContentRepository;
import com.shoptopus.core.page.Page;
import com.shoptopus.core.page.repository.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.i18n.languages=en,de")
class PublicPageApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Test
    void pages_are_delivered_for_menu_and_by_slug_with_locale_aware_content() {
        Page menuPage = new Page();
        menuPage.setTitle(new LinkedHashMap<>(Map.of("en", "About us", "de", "Uber uns")));
        menuPage.setSlug("about_us");
        menuPage.setStatus(1);
        menuPage.setShowInMenu(true);
        menuPage.setVersion(1);
        menuPage = pageRepository.save(menuPage);

        Content english = new Content();
        english.setContentableType("page");
        english.setContentableId(menuPage.getId());
        english.setLanguageId(1L);
        english.setType(0);
        english.setText("<p>English body</p>");
        english.setSortOrder(0);
        contentRepository.save(english);

        Content german = new Content();
        german.setContentableType("page");
        german.setContentableId(menuPage.getId());
        german.setLanguageId(2L);
        german.setType(0);
        german.setText("<p>Deutscher Inhalt</p>");
        german.setSortOrder(0);
        contentRepository.save(german);

        Page hiddenPage = new Page();
        hiddenPage.setTitle(Map.of("en", "Hidden page"));
        hiddenPage.setSlug("hidden_page");
        hiddenPage.setStatus(1);
        hiddenPage.setShowInMenu(false);
        hiddenPage.setVersion(1);
        pageRepository.save(hiddenPage);

        Page previewPage = new Page();
        previewPage.setTitle(Map.of("en", "Preview page"));
        previewPage.setSlug("preview_page");
        previewPage.setStatus(0);
        previewPage.setShowInMenu(true);
        previewPage.setVersion(1);
        pageRepository.save(previewPage);

        var menuRes = restTemplate.getForEntity("/api/pages", Map.class);
        assertThat(menuRes.getStatusCode().is2xxSuccessful()).isTrue();
        var menuItems = (List<Map<String, Object>>) menuRes.getBody().get("data");
        assertThat(menuItems).extracting(item -> item.get("slug")).contains("about_us").doesNotContain("hidden_page", "preview_page");

        var allRes = restTemplate.getForEntity("/api/pages?menu_only=false", Map.class);
        assertThat(allRes.getStatusCode().is2xxSuccessful()).isTrue();
        var allItems = (List<Map<String, Object>>) allRes.getBody().get("data");
        assertThat(allItems).extracting(item -> item.get("slug")).contains("about_us", "hidden_page").doesNotContain("preview_page");

        HttpHeaders deHeaders = new HttpHeaders();
        deHeaders.set(HttpHeaders.ACCEPT_LANGUAGE, "de");
        var deRes = restTemplate.exchange("/api/pages/about_us", HttpMethod.GET, new HttpEntity<>(deHeaders), Map.class);
        assertThat(deRes.getStatusCode().is2xxSuccessful()).isTrue();
        var deData = (Map<String, Object>) deRes.getBody().get("data");
        assertThat(deData.get("title")).isEqualTo("Uber uns");
        var deContents = (List<Map<String, Object>>) deData.get("contents");
        assertThat(deContents).hasSize(1);
        assertThat(deContents.get(0).get("text")).isEqualTo("<p>Deutscher Inhalt</p>");

        HttpHeaders frHeaders = new HttpHeaders();
        frHeaders.set(HttpHeaders.ACCEPT_LANGUAGE, "fr");
        var frRes = restTemplate.exchange("/api/pages/about_us", HttpMethod.GET, new HttpEntity<>(frHeaders), Map.class);
        assertThat(frRes.getStatusCode().is2xxSuccessful()).isTrue();
        var frData = (Map<String, Object>) frRes.getBody().get("data");
        assertThat(frData.get("title")).isEqualTo("About us");
        var frContents = (List<Map<String, Object>>) frData.get("contents");
        assertThat(frContents).hasSize(1);
        assertThat(frContents.get(0).get("text")).isEqualTo("<p>English body</p>");
    }
}

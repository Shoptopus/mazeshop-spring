package com.shoptopus.core.catalog;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogApiTest extends IntegrationTestBase {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fuzzy_search_and_translation_resolution_work() {
        var p = new Product();
        p.setName(Map.of("en", "Maze Lamp", "de", "Labyrinth Lampe"));
        p.setSlug("maze-lamp");
        p.setShortDescription(Map.of("en", "Short", "de", "Kurz"));
        p.setDescription(Map.of("en", "Desc", "de", "Beschreibung"));
        p.setPrice(BigDecimal.valueOf(99));
        p.setStock(5L);
        p.setVirtual(false);
        productRepository.save(p);

        var response = catalogService.getProduct(p.getId(), Locale.GERMAN);

        assertThat(response.name()).isEqualTo("Labyrinth Lampe");

        var fuzzy = catalogService.getProducts(new com.shoptopus.core.catalog.dto.ProductFilterRequest("Labyrnth", null, null, null, 0, 20), Locale.GERMAN);
        assertThat(fuzzy).isNotEmpty();
    }

    @Test
    void api_resolves_translations_from_accept_language() {
        var p = new Product();
        p.setName(Map.of("en", "Desk", "de", "Schreibtisch"));
        p.setSlug("desk-" + UUID.randomUUID());
        p.setShortDescription(Map.of("en", "Short", "de", "Kurz"));
        p.setDescription(Map.of("en", "Desc", "de", "Beschreibung"));
        p.setPrice(BigDecimal.valueOf(99));
        p.setStock(5L);
        p.setVirtual(false);
        productRepository.save(p);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept-Language", "de");
        var response = restTemplate.exchange("/api/product/" + p.getId(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        var data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("name")).isEqualTo("Schreibtisch");
    }
}

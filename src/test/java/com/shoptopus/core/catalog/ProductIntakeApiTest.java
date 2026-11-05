package com.shoptopus.core.catalog;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIntakeApiTest extends IntegrationTestBase {

    private final List<UUID> seededCategoryIds = new ArrayList<>();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void removeSeededCategories() {
        // HTTP requests commit separately, so fixture cleanup cannot rely on a test rollback.
        for (int i = seededCategoryIds.size() - 1; i >= 0; i--) {
            jdbc.update("delete from product_categories where id = ?::uuid", seededCategoryIds.get(i).toString());
        }
    }

    @Test
    void authenticated_users_with_products_manage_can_create_draft_products_from_plain_payloads() {
        String token = token();
        UUID homeId = seedCategory(null, "Home & Living");
        UUID storageId = seedCategory(homeId, "Storage & Organisation");
        UUID boxesId = seedCategory(storageId, "Boxes", "home-living-storage-organisation-boxes");
        UUID giftsId = seedCategory(null, "Gifts");
        UUID personalisedId = seedCategory(giftsId, "Personalised Gifts");
        UUID homeDecorId = seedCategory(null, "Home Decor");

        var response = authed(token, HttpMethod.POST, "/api/products", Map.of(
                "name", "Personalised Wooden Sliding Lid Box, Laser Cut Storage Case, Custom Engraved Gift Box",
                "price_gbp", 14.99,
                "weight_grams", 180,
                "headline", "Minimalist Personalised Wooden Keepsake Box",
                "subtitle", "A sleek sliding lid box, crafted from laser-cut wood and ready for your custom message",
                "short_description", "A handcrafted laser-cut wooden box with a smooth sliding lid.",
                "description", "Detailed product description\n\nwith paragraphs.",
                "meta_tags", List.of("wooden box", "engraved box"),
                "categories", List.of(
                        "home & living > storage & organisation > boxes",
                        "GIFTS > PERSONALISED GIFTS",
                        "Home & Living > Home Decor > Decorative Boxes"
                ),
                "stock", 3
        ));

        assert2xx(response);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        UUID productId = UUID.fromString(String.valueOf(data.get("id")));

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName()).containsEntry("en", "Personalised Wooden Sliding Lid Box, Laser Cut Storage Case, Custom Engraved Gift Box");
        assertThat(product.getHeadline()).containsEntry("en", "Minimalist Personalised Wooden Keepsake Box");
        assertThat(product.getSubtitle()).containsEntry("en", "A sleek sliding lid box, crafted from laser-cut wood and ready for your custom message");
        assertThat(product.getShortDescription()).containsEntry("en", "A handcrafted laser-cut wooden box with a smooth sliding lid.");
        assertThat(product.getDescription()).containsEntry("en", "Detailed product description\n\nwith paragraphs.");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("14.99"));
        assertThat(product.getWeight()).isEqualTo(180);
        assertThat(product.getStock()).isEqualTo(3L);
        assertThat(product.getStatus()).isEqualTo(0);
        assertThat(product.getSlug()).startsWith("personalised-wooden-sliding-lid-box").matches(".*-[a-f0-9]{8}$");
        assertThat(product.getMetaTags()).containsExactly("wooden box", "engraved box");
        assertThat(product.getCategories()).extracting(ProductCategory::getId)
                .containsExactlyInAnyOrder(homeId, storageId, boxesId, giftsId, personalisedId, homeDecorId);
    }

    @Test
    void product_intake_does_not_attach_unrelated_categories_with_similar_slug_suffixes() {
        String token = token();
        UUID homeId = seedCategory(null, "Home & Living");
        UUID kitchenId = seedCategory(homeId, "Kitchen & Dining");
        UUID barwareId = seedCategory(kitchenId, "Drink & Barware");
        UUID coastersId = seedCategory(barwareId, "Coasters");
        UUID decorId = seedCategory(homeId, "Home Decor");
        seedCategory(null, "Home Decor");
        UUID giftsId = seedCategory(null, "Gifts");
        seedCategory(null, "Drink & Barware");
        seedCategory(null, "Drink & Barware");
        seedCategory(null, "Best Men Gifts");
        seedCategory(null, "Bridesmaid Home Decor");
        seedCategory(null, "Bridesmaids' Gifts");
        seedCategory(null, "Car Coasters");
        seedCategory(null, "Groomsmen Home Decor");
        seedCategory(null, "Religious Home & Decor");
        seedCategory(null, "Spa Kits & Gifts");

        var response = authed(token, HttpMethod.POST, "/api/products", Map.of(
                "name", "Coaster Set",
                "price_gbp", 14.99,
                "weight_grams", 180,
                "headline", "Coaster Set",
                "subtitle", "Coaster Set",
                "short_description", "Coaster Set",
                "description", "Coaster Set",
                "categories", List.of(
                        "Home & Living > Kitchen & Dining > Drink & Barware > Coasters",
                        "Home & Living > Home Decor > Decorative Objects",
                        "Gifts > Housewarming Gifts"
                ),
                "stock", 3
        ));

        assert2xx(response);
        UUID productId = UUID.fromString(String.valueOf(((Map<String, Object>) response.getBody().get("data")).get("id")));
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getCategories()).extracting(ProductCategory::getId)
                .containsExactlyInAnyOrder(homeId, kitchenId, barwareId, coastersId, decorId, giftsId);
        assertThat(product.getCategories()).extracting(category -> category.getName().get("en"))
                .containsExactlyInAnyOrder("Home & Living", "Kitchen & Dining", "Drink & Barware", "Coasters", "Home Decor", "Gifts");
    }

    @Test
    void product_intake_flattens_all_rows_into_a_deduplicated_category_array() {
        String token = token();
        UUID homeId = seedCategory(null, "Home & Living");
        UUID kitchenId = seedCategory(homeId, "Kitchen & Dining");
        UUID barwareId = seedCategory(kitchenId, "Drink & Barware");
        UUID coastersId = seedCategory(barwareId, "Coasters");
        UUID decorId = seedCategory(homeId, "Home Decor");
        UUID giftsId = seedCategory(null, "Gifts");
        UUID housewarmingId = seedCategory(giftsId, "Housewarming Gifts");

        var response = authed(token, HttpMethod.POST, "/api/products", Map.of(
                "name", "Flat category product",
                "price_gbp", 19.99,
                "weight_grams", 250,
                "headline", "Flat category product",
                "subtitle", "Flat category product",
                "short_description", "Flat category product",
                "description", "Flat category product",
                "categories", List.of(
                        "Home & Living > Kitchen & Dining > Drink & Barware > Coasters",
                        "Home & Living > Home Decor > Decorative Objects",
                        "Gifts > Housewarming Gifts",
                        "Home & Living > Kitchen & Dining > Drink & Barware > Coasters"
                ),
                "stock", 4
        ));

        assert2xx(response);
        UUID productId = UUID.fromString(String.valueOf(((Map<String, Object>) response.getBody().get("data")).get("id")));
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getCategories()).extracting(ProductCategory::getId)
                .containsExactlyInAnyOrder(homeId, kitchenId, barwareId, coastersId, decorId, giftsId, housewarmingId);
    }

    @Test
    void product_intake_returns_plain_english_field_errors() {
        String token = token();

        var response = authed(token, HttpMethod.POST, "/api/products", Map.of(
                "name", "",
                "price_gbp", "abc",
                "weight_grams", 12.5,
                "headline", "",
                "subtitle", "",
                "short_description", "",
                "description", "",
                "meta_tags", "not-a-list",
                "categories", List.of("  "),
                "stock", -1
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("user_message")).isEqualTo("Validation failed");
        assertThat(response.getBody().get("developer_message")).isEqualTo("Please correct the highlighted fields and try again.");
        Map<String, Object> errors = (Map<String, Object>) response.getBody().get("errors");
        assertThat(errors.keySet()).contains("name", "price_gbp", "weight_grams", "headline", "subtitle", "short_description", "description", "meta_tags", "stock");
        assertThat((List<String>) errors.get("name")).contains("Name is required.");
        assertThat((List<String>) errors.get("price_gbp")).contains("Price in GBP must be a number.");
        assertThat((List<String>) errors.get("weight_grams")).contains("Weight in grams must be a whole number.");
        assertThat((List<String>) errors.get("stock")).contains("Stock must be 0 or greater.");
    }

    @Test
    void product_intake_requires_authentication_and_products_manage_permission() {
        var unauthenticated = restTemplate.postForEntity("/api/products", Map.of(), Map.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String email = "product-intake-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Blocked",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "catalog_viewer");
        assignPermissionToRole("catalog_viewer", "products.view");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        var forbidden = authed(token, HttpMethod.POST, "/api/products", Map.of(
                "name", "Blocked",
                "price_gbp", 10,
                "weight_grams", 100,
                "headline", "Blocked",
                "subtitle", "Blocked",
                "short_description", "Blocked",
                "description", "Blocked",
                "stock", 1
        ));
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String token() {
        String email = "product-intake-admin-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Product",
                "last_name", "Intake",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "catalog_manager");
        assignPermissionToRole("catalog_manager", "products.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    private void assignRole(String email, String roleName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        String userId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, email);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?::uuid) on conflict do nothing", roleId, userId);
    }

    private void assignPermissionToRole(String roleName, String permissionName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        jdbc.update("insert into permissions(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", permissionName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        Integer permissionId = jdbc.queryForObject("select id from permissions where name = ? limit 1", Integer.class, permissionName);
        jdbc.update("insert into role_has_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", roleId, permissionId);
    }

    private ResponseEntity<Map> authed(String token, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private void assert2xx(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Expected 2xx, got " + response.getStatusCode() + " body=" + response.getBody())
                .isTrue();
    }

    private UUID seedCategory(UUID parentId, String name) {
        return seedCategory(parentId, name, name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "") + "-" + UUID.randomUUID());
    }

    private UUID seedCategory(UUID parentId, String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into product_categories(id, parent_id, name, slug, description, enabled, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())
                """,
                id.toString(),
                parentId == null ? null : parentId.toString(),
                "{\"en\":\"" + name.replace("\"", "\\\"") + "\"}",
                slug,
                "{\"en\":\"" + name.replace("\"", "\\\"") + "\"}");
        seededCategoryIds.add(id);
        return id;
    }
}

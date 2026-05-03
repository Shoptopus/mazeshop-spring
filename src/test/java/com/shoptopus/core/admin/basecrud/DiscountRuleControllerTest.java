package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountRuleControllerTest extends AdminControllerTestBase {

    @Test
    void discount_rules_can_be_listed_and_shown() {
        String token = token();
        seedDiscountRule(Map.of("en", "5% off", "de", "5% rabatt"), 1, "5.00", true,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(30));
        UUID ruleId = seedDiscountRule(Map.of("en", "5 pounds off", "de", "5 pfund rabatt"), 2, "5.00", true,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(30));

        var list = authed(token, HttpMethod.GET, "/api/admin/discount-rules", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> ruleId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("type")).isEqualTo(2);
        assertThat(row.get("value")).isEqualTo("GBP 5");
        assertThat(row.get("valid")).isEqualTo(true);

        var show = authed(token, HttpMethod.GET, "/api/admin/discount-rule/" + ruleId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(ruleId.toString());
        assertThat(data.get("name")).isEqualTo(Map.of("en", "5 pounds off", "de", "5 pfund rabatt"));
        assertThat(data.get("amount")).isEqualTo("GBP 5");
        assertThat(data.get("products")).isInstanceOf(List.class);
        assertThat(data.get("categories")).isInstanceOf(List.class);
        assertThat(data.get("notes")).isInstanceOf(List.class);
    }

    @Test
    void discount_rule_can_be_created_updated_attached_and_deleted() {
        String token = token();
        OffsetDateTime validFrom = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime validUntil = validFrom.plusMonths(1);

        var create = authed(token, HttpMethod.POST, "/api/admin/discount-rule", Map.of(
                "name", "{\"en\":\"5% off\",\"de\":\"5% rabatt\"}",
                "type", 1,
                "amount", 5,
                "valid_from", validFrom.toString(),
                "valid_until", validUntil.toString(),
                "enabled", true
        ));
        assert2xx(create);
        UUID ruleId = UUID.fromString(String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id")));

        Map<String, Object> updatePayload = Map.of(
                "name", "{\"en\":\"8.5% off UPDATED\",\"de\":\"8.5% rabatt AKTUALISIERT\"}",
                "type", 1,
                "amount", 8.5,
                "valid_from", validFrom.toString(),
                "valid_until", validUntil.toString()
        );
        assert2xx(authed(token, HttpMethod.PATCH, "/api/admin/discount-rule/" + ruleId, updatePayload));

        BigDecimal amount = jdbc.queryForObject("select amount from discount_rules where id = ?::uuid", BigDecimal.class, ruleId.toString());
        assertThat(amount).isEqualByComparingTo("8.50");

        UUID productId = seedProduct("discount-product-" + UUID.randomUUID());
        UUID categoryId = seedCategory("discount-category-" + UUID.randomUUID());

        var attachProduct = authed(token, HttpMethod.POST, "/api/admin/discount-rule/" + ruleId + "/products/" + productId, null);
        assert2xx(attachProduct);
        assertThat(((List<?>) ((Map<?, ?>) attachProduct.getBody().get("data")).get("products"))).hasSize(1);

        var attachCategory = authed(token, HttpMethod.POST, "/api/admin/discount-rule/" + ruleId + "/product-categories/" + categoryId, null);
        assert2xx(attachCategory);
        assertThat(((List<?>) ((Map<?, ?>) attachCategory.getBody().get("data")).get("categories"))).hasSize(1);

        var availableProducts = authed(token, HttpMethod.GET, "/api/admin/discount-rule/" + ruleId + "/available-products", null);
        assert2xx(availableProducts);
        List<Map<?, ?>> productData = (List<Map<?, ?>>) ((Map<?, ?>) availableProducts.getBody().get("data")).get("data");
        assertThat(productData).extracting(item -> String.valueOf(item.get("id"))).doesNotContain(productId.toString());

        var availableCategories = authed(token, HttpMethod.GET, "/api/admin/discount-rule/" + ruleId + "/available-categories", null);
        assert2xx(availableCategories);
        List<Map<?, ?>> categoryData = (List<Map<?, ?>>) ((Map<?, ?>) availableCategories.getBody().get("data")).get("data");
        assertThat(categoryData).extracting(item -> String.valueOf(item.get("id"))).doesNotContain(categoryId.toString());

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/discount-rule/" + ruleId + "/products/" + productId, null));
        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/discount-rule/" + ruleId + "/product-categories/" + categoryId, null));

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/discount-rule/" + ruleId, null));
        Integer deleted = jdbc.queryForObject("select count(*) from discount_rules where id = ?::uuid and deleted_at is not null", Integer.class, ruleId.toString());
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void discount_rule_validation_is_enforced() {
        String token = token();
        OffsetDateTime validFrom = OffsetDateTime.now();
        OffsetDateTime validUntil = validFrom.plusDays(7);

        var create = authed(token, HttpMethod.POST, "/api/admin/discount-rule", Map.of(
                "name", "{\"en\":\"5% off\"}",
                "type", 1,
                "valid_from", validFrom.toString(),
                "valid_until", validUntil.toString(),
                "enabled", true
        ));
        assertValidationFailed(create, "amount field is required");

        UUID ruleId = seedDiscountRule(Map.of("en", "Update me"), 1, "5.00", true, validFrom, validUntil);
        var update = authed(token, HttpMethod.PATCH, "/api/admin/discount-rule/" + ruleId, Map.of(
                "amount", "Thirty two"
        ));
        assertValidationFailed(update, "amount must be a number");
    }

    @Test
    void discount_rule_crud_requires_super_admin_role() {
        String email = "discount-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Discount",
                "last_name", "Customer",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "customer");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        var create = authed(token, HttpMethod.POST, "/api/admin/discount-rule", Map.of(
                "name", "{\"en\":\"5% off\"}",
                "type", 1,
                "amount", 5,
                "valid_from", OffsetDateTime.now().toString(),
                "valid_until", OffsetDateTime.now().plusDays(7).toString(),
                "enabled", true
        ));
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        UUID ruleId = seedDiscountRule(Map.of("en", "Nope"), 1, "5.00", true, OffsetDateTime.now(), OffsetDateTime.now().plusDays(7));
        var update = authed(token, HttpMethod.PATCH, "/api/admin/discount-rule/" + ruleId, Map.of("amount", 10));
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/discount-rule/" + ruleId, null);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedDiscountRule(Map<String, String> name, int type, String amount, boolean enabled, OffsetDateTime validFrom, OffsetDateTime validUntil) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into discount_rules(id, type, name, amount, valid_from, valid_until, enabled, slug, created_at, updated_at)
                values (?::uuid, ?, ?::jsonb, ?, ?, ?, ?, ?, now(), now())
                """,
                id, type, "{\"en\":\"" + name.getOrDefault("en", "") + "\"" + (name.containsKey("de") ? ",\"de\":\"" + name.get("de") + "\"" : "") + "}",
                new BigDecimal(amount), validFrom, validUntil, enabled, "discount-rule-" + id);
        return UUID.fromString(id);
    }

    private UUID seedProduct(String slug) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into products(id, name, slug, short_description, description, price, status, purchase_count, stock, virtual, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, 10, 0, 0, 10, false, now(), now())
                """,
                id, "{\"en\":\"Product\"}", slug, "{\"en\":\"Short\"}", "{\"en\":\"Description\"}");
        return UUID.fromString(id);
    }

    private UUID seedCategory(String slug) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into product_categories(id, name, slug, description, enabled, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())
                """,
                id, "{\"en\":\"Category\"}", slug, "{\"en\":\"Description\"}");
        return UUID.fromString(id);
    }
}

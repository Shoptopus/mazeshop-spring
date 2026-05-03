package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRuleControllerTest extends AdminControllerTestBase {

    @Test
    void delivery_rules_can_be_listed_and_shown() {
        String token = token();
        UUID deliveryTypeId = seedDeliveryType();
        seedDeliveryRule(deliveryTypeId, List.of("AA1"), 0, 1000, "5.00", "10.00", "51.400000", "-2.500000");
        UUID deliveryRuleId = seedDeliveryRule(deliveryTypeId, List.of("BS10", "NX9"), 10, 5000, "10.00", "100.00", "51.454500", "-2.587900");

        var list = authed(token, HttpMethod.GET, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rules", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> deliveryRuleId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("postcodes")).isEqualTo(List.of("BS10", "NX9"));
        assertThat(row.get("min_weight")).isEqualTo(10);
        assertThat(row.get("max_weight")).isEqualTo(5000);

        var show = authed(token, HttpMethod.GET, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule/" + deliveryRuleId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(deliveryRuleId.toString());
        assertThat(data.get("min_weight")).isEqualTo(10);
        assertThat(data.get("max_weight")).isEqualTo(5000);
        assertThat(data.get("min_distance")).isEqualTo(10.00);
        assertThat(data.get("max_distance")).isEqualTo(100.00);
        assertThat(data.get("lat")).isEqualTo(51.454500);
        assertThat(data.get("lon")).isEqualTo(-2.587900);
    }

    @Test
    void delivery_rule_can_be_created_updated_and_deleted() {
        String token = token();
        UUID deliveryTypeId = seedDeliveryType();

        var create = authed(token, HttpMethod.POST, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule", Map.of(
                "postcodes", List.of("BS10", "NX9"),
                "enabled", true,
                "min_weight", 0,
                "max_weight", 5000,
                "min_distance", 10,
                "max_distance", 100,
                "lat", 51.4545,
                "lon", -2.5879
        ));
        assert2xx(create);
        UUID deliveryRuleId = UUID.fromString(String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id")));

        var update = authed(token, HttpMethod.PATCH, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule/" + deliveryRuleId, Map.of(
                "postcodes", List.of("SW1", "E1"),
                "enabled", false,
                "min_weight", 1,
                "max_weight", 3000,
                "min_distance", 5,
                "max_distance", 50,
                "lat", 52.0,
                "lon", -1.5
        ));
        assert2xx(update);

        assertThat(jdbc.queryForObject("select postcodes::text from delivery_rules where id = ?::uuid", String.class, deliveryRuleId.toString())).contains("SW1");
        assertThat(jdbc.queryForObject("select enabled from delivery_rules where id = ?::uuid", Boolean.class, deliveryRuleId.toString())).isFalse();
        assertThat(jdbc.queryForObject("select min_weight from delivery_rules where id = ?::uuid", Integer.class, deliveryRuleId.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select lat from delivery_rules where id = ?::uuid", BigDecimal.class, deliveryRuleId.toString())).isEqualByComparingTo("52.000000");

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule/" + deliveryRuleId, null));
        Integer deleted = jdbc.queryForObject("select count(*) from delivery_rules where id = ?::uuid and deleted_at is not null", Integer.class, deliveryRuleId.toString());
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void delivery_rule_validation_is_enforced() {
        String token = token();
        UUID deliveryTypeId = seedDeliveryType();

        var invalidCreate = authed(token, HttpMethod.POST, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule", Map.of(
                "lat", "hello",
                "lon", -2.5879
        ));
        assertValidationFailed(invalidCreate, "lat must be a number");

        UUID deliveryRuleId = seedDeliveryRule(deliveryTypeId, List.of("BS10"), 0, 5000, "10.00", "100.00", "51.454500", "-2.587900");
        var invalidUpdate = authed(token, HttpMethod.PATCH, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule/" + deliveryRuleId, Map.of(
                "lat", 51.4545,
                "lon", "invalid"
        ));
        assertValidationFailed(invalidUpdate, "lon must be a number");
    }

    @Test
    void delivery_rule_crud_requires_super_admin_role() {
        String email = "delivery-rule-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Delivery",
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

        UUID deliveryTypeId = seedDeliveryType();
        var create = authed(token, HttpMethod.POST, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule", Map.of("lat", 51.4545));
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        UUID deliveryRuleId = seedDeliveryRule(deliveryTypeId, List.of("BS10"), 0, 5000, "10.00", "100.00", "51.454500", "-2.587900");
        assertThat(authed(token, HttpMethod.PATCH, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule/" + deliveryRuleId, Map.of("lon", -1.5)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.DELETE, "/api/admin/delivery-type/" + deliveryTypeId + "/delivery-rule/" + deliveryRuleId, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedDeliveryType() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into delivery_types(id, name, slug, description, price, enabled, enabled_by_default_on_creation, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, 5, true, true, now(), now())
                """,
                id, "{\"en\":\"Express\"}", "delivery-type-" + id, "{\"en\":\"Express delivery\"}");
        return UUID.fromString(id);
    }

    private UUID seedDeliveryRule(UUID deliveryTypeId, List<String> postcodes, int minWeight, int maxWeight, String minDistance, String maxDistance, String lat, String lon) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into delivery_rules(id, delivery_type_id, name, slug, postcodes, min_weight, max_weight, min_distance, max_distance, lat, lon, price, enabled, created_at, updated_at)
                values (?::uuid, ?::uuid, '{}'::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, 0, true, now(), now())
                """,
                id, deliveryTypeId.toString(), "delivery-rule-" + id, jsonArray(postcodes), minWeight, maxWeight,
                new BigDecimal(minDistance), new BigDecimal(maxDistance), new BigDecimal(lat), new BigDecimal(lon));
        return UUID.fromString(id);
    }

    private String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}

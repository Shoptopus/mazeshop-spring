package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTypeControllerTest extends AdminControllerTestBase {

    @Test
    void delivery_types_can_be_listed_and_shown() {
        String token = token();
        seedDeliveryType(Map.of("en", "Standard"), Map.of("en", "Standard shipping"), "10.00", true, true);
        UUID deliveryTypeId = seedDeliveryType(Map.of("en", "Express", "de", "Express"), Map.of("en", "Very quick", "de", "Sehr schnell"), "15.00", true, false);

        var list = authed(token, HttpMethod.GET, "/api/admin/delivery-types", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> deliveryTypeId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("price")).isEqualTo(15.00);
        assertThat(row.get("enabled_by_default_on_creation")).isEqualTo(false);

        var show = authed(token, HttpMethod.GET, "/api/admin/delivery-type/" + deliveryTypeId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(deliveryTypeId.toString());
        assertThat(data.get("name")).isEqualTo(Map.of("en", "Express", "de", "Express"));
        assertThat(data.get("description")).isEqualTo(Map.of("en", "Very quick", "de", "Sehr schnell"));
        assertThat(data.get("price")).isEqualTo(15.00);
        assertThat(data.get("enabled")).isEqualTo(true);
        assertThat(data.get("enabled_by_default_on_creation")).isEqualTo(false);
        assertThat(data.get("notes")).isInstanceOf(List.class);
    }

    @Test
    void delivery_type_can_be_created_updated_and_deleted() {
        String token = token();

        var create = authed(token, HttpMethod.POST, "/api/admin/delivery-type", Map.of(
                "name", "{\"en\":\"Express overnight\",\"de\":\"Express Ubernacht\"}",
                "description", "{\"en\":\"Very quick\",\"de\":\"Sehr schnell\"}",
                "price", 15,
                "enabled", true,
                "enabled_by_default_on_creation", true
        ));
        assert2xx(create);
        UUID deliveryTypeId = UUID.fromString(String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id")));

        var update = authed(token, HttpMethod.PATCH, "/api/admin/delivery-type/" + deliveryTypeId, Map.of(
                "name", "{\"en\":\"Express overnight UPDATED\",\"de\":\"Express Ubernacht AKTUALISIERT\"}",
                "description", "{\"en\":\"Very quick UPDATED\",\"de\":\"Sehr schnell AKTUALISIERT\"}",
                "price", 20,
                "enabled", false,
                "enabled_by_default_on_creation", false
        ));
        assert2xx(update);

        String englishName = jdbc.queryForObject("select name->>'en' from delivery_types where id = ?::uuid", String.class, deliveryTypeId.toString());
        String germanDescription = jdbc.queryForObject("select description->>'de' from delivery_types where id = ?::uuid", String.class, deliveryTypeId.toString());
        BigDecimal price = jdbc.queryForObject("select price from delivery_types where id = ?::uuid", BigDecimal.class, deliveryTypeId.toString());
        Boolean enabled = jdbc.queryForObject("select enabled from delivery_types where id = ?::uuid", Boolean.class, deliveryTypeId.toString());
        Boolean enabledByDefault = jdbc.queryForObject("select enabled_by_default_on_creation from delivery_types where id = ?::uuid", Boolean.class, deliveryTypeId.toString());

        assertThat(englishName).isEqualTo("Express overnight UPDATED");
        assertThat(germanDescription).isEqualTo("Sehr schnell AKTUALISIERT");
        assertThat(price).isEqualByComparingTo("20.00");
        assertThat(enabled).isFalse();
        assertThat(enabledByDefault).isFalse();

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/delivery-type/" + deliveryTypeId, null));
        Integer deleted = jdbc.queryForObject("select count(*) from delivery_types where id = ?::uuid and deleted_at is not null", Integer.class, deliveryTypeId.toString());
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void delivery_type_validation_is_enforced() {
        String token = token();

        var missingName = authed(token, HttpMethod.POST, "/api/admin/delivery-type", Map.of(
                "description", "{\"en\":\"Very quick\"}",
                "price", 15,
                "enabled", true
        ));
        assertValidationFailed(missingName, "name field is required");

        UUID deliveryTypeId = seedDeliveryType(Map.of("en", "Standard"), Map.of("en", "Base"), "10.00", true, true);
        var invalidPrice = authed(token, HttpMethod.PATCH, "/api/admin/delivery-type/" + deliveryTypeId, Map.of("price", "twenty"));
        assertValidationFailed(invalidPrice, "price must be a number");
    }

    @Test
    void delivery_type_crud_requires_super_admin_role() {
        String email = "delivery-type-customer-" + UUID.randomUUID() + "@example.com";
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

        var create = authed(token, HttpMethod.POST, "/api/admin/delivery-type", Map.of("name", "{\"en\":\"Express\"}"));
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        UUID deliveryTypeId = seedDeliveryType(Map.of("en", "Standard"), Map.of("en", "Base"), "10.00", true, true);
        assertThat(authed(token, HttpMethod.PATCH, "/api/admin/delivery-type/" + deliveryTypeId, Map.of("price", 20)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.DELETE, "/api/admin/delivery-type/" + deliveryTypeId, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedDeliveryType(Map<String, String> name, Map<String, String> description, String price, boolean enabled, boolean enabledByDefault) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into delivery_types(id, name, slug, description, price, enabled, enabled_by_default_on_creation, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, ?, ?, ?, now(), now())
                """,
                id, json(name), "delivery-type-" + id, json(description), new BigDecimal(price), enabled, enabledByDefault);
        return UUID.fromString(id);
    }

    private String json(Map<String, String> value) {
        return value.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}

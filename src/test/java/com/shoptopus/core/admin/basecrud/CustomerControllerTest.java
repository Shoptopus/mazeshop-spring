package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerControllerTest extends AdminControllerTestBase {

    @Test
    void test_can_list_and_show_customers() {
        String token = token();
        var list = authed(token, HttpMethod.GET, "/api/admin/customers", null);
        assert2xx(list);

        UUID customerId = UUID.fromString(jdbc.queryForObject("select id::text from users where deleted_at is null limit 1", String.class));
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> customerId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.containsKey("email")).isTrue();
        assertThat(row.containsKey("first_name")).isTrue();
        assertThat(row.containsKey("last_name")).isTrue();

        var show = authed(token, HttpMethod.GET, "/api/admin/customer/" + customerId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(customerId.toString());
        assertThat(data.containsKey("phone")).isTrue();
        assertThat(data.containsKey("favorite")).isTrue();
        assertThat(data.containsKey("temporary")).isTrue();
    }

    @Test
    void customers_require_customers_view_permission() {
        String email = "customers-viewer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Customers",
                "last_name", "Viewer",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "customer_viewer");
        assignPermissionToRole("customer_viewer", "customers.view");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/customers", null).getStatusCode().is2xxSuccessful()).isTrue();
    }
}

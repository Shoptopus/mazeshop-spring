package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartControllerTest extends AdminControllerTestBase {

    @Test
    void carts_can_be_created_listed_shown_updated_and_deleted() {
        String token = token();
        String userId = jdbc.queryForObject("select id::text from users where deleted_at is null limit 1", String.class);

        var create = authed(token, HttpMethod.POST, "/api/admin/cart", Map.of(
                "user_id", userId,
                "ip_address", "127.0.0.1",
                "slug", "admin-cart-" + UUID.randomUUID()
        ));
        assert2xx(create);
        String cartId = String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id"));

        var list = authed(token, HttpMethod.GET, "/api/admin/carts", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> cartId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("items_count")).isEqualTo(0);

        var show = authed(token, HttpMethod.GET, "/api/admin/cart/" + cartId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(cartId);
        assertThat(data.get("items")).isInstanceOf(List.class);

        assert2xx(authed(token, HttpMethod.PATCH, "/api/admin/cart/" + cartId, Map.of("ip_address", "10.0.0.2")));
        String ipAddress = jdbc.queryForObject("select ip_address from carts where id = ?::uuid", String.class, cartId);
        assertThat(ipAddress).isEqualTo("10.0.0.2");

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/cart/" + cartId, null));
        Integer rows = jdbc.queryForObject("select count(*) from carts where id = ?::uuid", Integer.class, cartId);
        assertThat(rows).isEqualTo(0);
    }

    @Test
    void carts_require_cart_permissions() {
        String email = "carts-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Carts",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "cart_manager");
        assignPermissionToRole("cart_manager", "carts.view");
        assignPermissionToRole("cart_manager", "carts.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/carts", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/cart", Map.of("slug", "cart-" + UUID.randomUUID())).getStatusCode().is2xxSuccessful()).isTrue();
    }
}

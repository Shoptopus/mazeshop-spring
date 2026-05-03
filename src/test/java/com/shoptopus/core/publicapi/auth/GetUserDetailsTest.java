package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetUserDetailsTest extends AuthApiTestBase {

    @Test
    void unauthenticated_users_cannot_access_their_details() {
        var res = exchange(HttpMethod.GET, "/api/auth/details", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().get("developer_message")).isEqualTo("Authentication required");
        assertThat(((Number) res.getBody().get("error_code")).intValue()).isEqualTo(1101);
    }

    @Test
    void authenticated_users_can_get_required_fields_with_unread_notifications_and_favorites() {
        String email = "details-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        UUID userId = userIdByEmail(email);

        String productId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into products(id, name, slug, short_description, description, price, stock, status, virtual, created_at, updated_at)
                values (?::uuid, '{}'::jsonb, ?, '{}'::jsonb, '{}'::jsonb, 10.00, 10, 0, false, now(), now())
                """, productId, "details-product-" + UUID.randomUUID());
        jdbc.update("insert into favorited_products(user_id, product_id) values (?::uuid, ?::uuid)", userId.toString(), productId);

        jdbc.update("""
                insert into notifications(id, user_id, type, title, data, read_at, created_at, updated_at)
                values (?::uuid, ?::uuid, 'order-complete', 'Unread', '{}'::jsonb, null, now(), now())
                """, UUID.randomUUID().toString(), userId.toString());
        jdbc.update("""
                insert into notifications(id, user_id, type, title, data, read_at, created_at, updated_at)
                values (?::uuid, ?::uuid, 'order-complete', 'Read', '{}'::jsonb, now(), now(), now())
                """, UUID.randomUUID().toString(), userId.toString());

        var res = exchange(HttpMethod.GET, "/api/auth/details", token, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> data = (Map<?, ?>) res.getBody().get("data");
        Map<?, ?> auth = (Map<?, ?>) data.get("auth");
        Map<?, ?> user = (Map<?, ?>) auth.get("user");

        assertThat(user.keySet().stream().map(String::valueOf).toList())
                .contains("id", "name", "first_name", "last_name", "email", "phone", "avatar", "is_verified", "cart", "notifications", "favorites");
        assertThat((java.util.List<?>) user.get("notifications")).hasSize(1);
        assertThat((java.util.List<?>) user.get("favorites")).hasSize(1);
    }
}

package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RatingControllerTest extends AdminControllerTestBase {

    @Test
    void ratings_can_be_listed_shown_and_deleted() {
        String token = token();
        UUID userId = UUID.fromString(jdbc.queryForObject("select id::text from users where deleted_at is null limit 1", String.class));
        UUID ratingId = UUID.randomUUID();
        jdbc.update("""
                insert into ratings(id, user_id, ratable_type, ratable_id, rating, language_prefix, description, title, enabled, verified, slug, created_at, updated_at)
                values (?::uuid, ?::uuid, 'product', ?::uuid, 5, 'en', 'Great product', 'Excellent', true, true, ?, now(), now())
                """,
                ratingId.toString(), userId.toString(), UUID.randomUUID().toString(), "rating-" + ratingId);

        var list = authed(token, HttpMethod.GET, "/api/admin/ratings", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> ratingId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("title")).isEqualTo("Excellent");
        assertThat(row.get("user_id")).isEqualTo(userId.toString());

        var show = authed(token, HttpMethod.GET, "/api/admin/rating/" + ratingId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(ratingId.toString());
        assertThat(data.get("description")).isEqualTo("Great product");
        assertThat(data.get("user")).isInstanceOf(Map.class);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/rating/" + ratingId, null);
        assert2xx(delete);
        Integer deleted = jdbc.queryForObject("select count(*) from ratings where id = ?::uuid and deleted_at is not null", Integer.class, ratingId.toString());
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void ratings_require_ratings_manage_permission() {
        String email = "ratings-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Ratings",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "content_manager");
        assignPermissionToRole("content_manager", "ratings.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/ratings", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingControllerTest extends AdminControllerTestBase {

    @Test
    void dashboard_and_reports_return_real_shapes() {
        String token = token();
        String productId = seedProduct();
        seedRating(productId, 5);
        seedRating(productId, 4);

        var dashboard = authed(token, HttpMethod.GET, "/api/admin/dashboard", null);
        assert2xx(dashboard);
        Map<?, ?> dashboardData = (Map<?, ?>) dashboard.getBody().get("data");
        assertThat(dashboardData.get("products")).isInstanceOf(Number.class);
        assertThat(dashboardData.get("ratings")).isInstanceOf(Number.class);

        var generic = authed(token, HttpMethod.POST, "/api/admin/report", Map.of());
        assert2xx(generic);
        assertThat(((Map<?, ?>) generic.getBody().get("data")).get("type")).isEqualTo("generic");

        var overview = authed(token, HttpMethod.POST, "/api/admin/reports/overview", Map.of());
        assert2xx(overview);
        assertThat(((Map<?, ?>) ((Map<?, ?>) overview.getBody().get("data")).get("totals")).get("products")).isInstanceOf(Number.class);

        var sales = authed(token, HttpMethod.POST, "/api/admin/reports/sales", Map.of());
        assert2xx(sales);
        assertThat(((Map<?, ?>) sales.getBody().get("data")).get("series")).isInstanceOf(java.util.List.class);

        var ratings = authed(token, HttpMethod.POST, "/api/admin/reports/product-ratings/" + productId, Map.of());
        assert2xx(ratings);
        Map<?, ?> ratingsData = (Map<?, ?>) ratings.getBody().get("data");
        assertThat(ratingsData.get("type")).isEqualTo("product-ratings");
        assertThat(ratingsData.get("total_ratings")).isEqualTo(2);

        var insights = authed(token, HttpMethod.GET, "/api/admin/product/" + productId + "/insights", null);
        assert2xx(insights);
        Map<?, ?> insightData = (Map<?, ?>) insights.getBody().get("data");
        assertThat(insightData.get("product_id")).isEqualTo(productId);
        assertThat(((Number) ((Map<?, ?>) insightData.get("insights")).get("ratings")).longValue()).isEqualTo(2L);
    }

    @Test
    void reporting_requires_reports_view_permission() {
        String email = "reports-viewer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Reports",
                "last_name", "Viewer",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "analyst");
        assignPermissionToRole("analyst", "reports.view");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/dashboard", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        String blockedEmail = "reports-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Reports",
                "last_name", "Blocked",
                "email", blockedEmail,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(blockedEmail, "customer");
        var blockedLogin = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", blockedEmail,
                "password", "password123"
        ), Map.class);
        String blockedToken = (String) ((Map<?, ?>) ((Map<?, ?>) blockedLogin.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(blockedToken, HttpMethod.GET, "/api/admin/dashboard", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(blockedToken, HttpMethod.GET, "/api/admin/product/" + seedProduct() + "/insights", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String seedProduct() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into products(id, name, slug, short_description, description, price, stock, virtual, status, purchase_count, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, 10, 4, false, 0, 0, now(), now())
                """,
                id, "{\"en\":\"Insight product\"}", "insight-product-" + id, "{\"en\":\"Short\"}", "{\"en\":\"Description\"}");
        return id;
    }

    private void seedRating(String productId, int rating) {
        String userId = UUID.randomUUID().toString();
        String ratingId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into users(id, first_name, last_name, email, password, name, slug, temporary, is_favorite, created_at, updated_at)
                values (?::uuid, 'Rate', 'User', ?, '$2a$10$abcdefghijklmnopqrstuv', 'Rate User', ?, false, false, now(), now())
                """, userId, "rating-" + ratingId + "@example.com", "rating-user-" + ratingId);
        jdbc.update("""
                insert into ratings(id, user_id, ratable_type, ratable_id, rating, language_prefix, description, title, enabled, verified, slug, created_at, updated_at)
                values (?::uuid, ?::uuid, 'product', ?::uuid, ?, 'en', 'desc', 'title', true, true, ?, now(), now())
                """, ratingId, userId, productId, rating, "rating-" + ratingId);
    }
}

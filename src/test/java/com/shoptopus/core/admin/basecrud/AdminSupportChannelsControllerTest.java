package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSupportChannelsControllerTest extends AdminControllerTestBase {

    @Test
    void email_users_send_email_and_notifications_behave() {
        String token = token();
        String userId = seedUser("channels");
        String adminUserId = jdbc.queryForObject(
                "select id::text from users where lower(email) like 'admin-basecrud-%@example.com' order by created_at desc limit 1",
                String.class
        );

        var getUsers = authed(token, HttpMethod.GET, "/api/admin/get-users", null);
        assert2xx(getUsers);
        assertThat(((Map<?, ?>) getUsers.getBody().get("data")).get("data")).isInstanceOf(List.class);

        var sendEmail = authed(token, HttpMethod.POST, "/api/admin/send-email", Map.of(
                "subject", "Admin subject",
                "message", "Admin message",
                "user_ids", List.of(userId)
        ));
        assert2xx(sendEmail);
        assertThat(((Map<?, ?>) sendEmail.getBody().get("data")).get("queued")).isEqualTo(1);

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/send-email", Map.of(
                "subject", "Admin inbox",
                "message", "Admin message",
                "user_ids", List.of(adminUserId)
        )));

        var notifications = authed(token, HttpMethod.GET, "/api/admin/notifications", null);
        assert2xx(notifications);
        List<Map<?, ?>> rows = (List<Map<?, ?>>) ((Map<?, ?>) notifications.getBody().get("data")).get("data");
        assertThat(rows).isNotEmpty();

        var clear = authed(token, HttpMethod.POST, "/api/admin/notifications", Map.of());
        assert2xx(clear);
        assertThat(((Map<?, ?>) clear.getBody().get("data")).get("cleared")).isInstanceOf(Number.class);
    }

    @Test
    void support_channel_permissions_and_validation_are_enforced() {
        String token = token();
        String userId = seedUser("channels-validation");

        var missingSubject = authed(token, HttpMethod.POST, "/api/admin/send-email", Map.of(
                "message", "Admin message",
                "user_ids", List.of(userId)
        ));
        assertValidationFailed(missingSubject, "subject field is required");

        String email = "support-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Support",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "support_manager");
        assignPermissionToRole("support_manager", "emails.manage");
        assignPermissionToRole("support_manager", "notifications.view");
        assignPermissionToRole("support_manager", "notifications.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String permittedToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
        assertThat(authed(permittedToken, HttpMethod.GET, "/api/admin/get-users", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        String blockedEmail = "support-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Support",
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
        assertThat(authed(blockedToken, HttpMethod.GET, "/api/admin/get-users", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(blockedToken, HttpMethod.GET, "/api/admin/notifications", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String seedUser(String prefix) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into users(id, first_name, last_name, email, password, name, slug, temporary, is_favorite, created_at, updated_at)
                values (?::uuid, ?, 'User', ?, '$2a$10$abcdefghijklmnopqrstuv', ?, ?, false, false, now(), now())
                """,
                id, prefix, prefix + "-" + id + "@example.com", prefix + " User", "user-" + id);
        return id;
    }
}

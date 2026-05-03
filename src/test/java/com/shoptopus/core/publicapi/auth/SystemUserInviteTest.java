package com.shoptopus.core.publicapi.auth;

import com.shoptopus.core.mail.EmailDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = "app.mail.invites-enabled=true")
class SystemUserInviteTest extends AuthApiTestBase {

    @MockBean
    private EmailDeliveryService emailDeliveryService;

    @Autowired
    private com.shoptopus.core.mail.AdminInviteMailService adminInviteMailService;

    @Test
    void can_invite_system_user_when_user_has_permission() {
        String adminEmail = "invite-admin-" + UUID.randomUUID() + "@example.com";
        String adminToken = registerAndLogin(adminEmail);
        UUID adminId = userIdByEmail(adminEmail);
        assignRole(adminId, "store_manager");
        assignPermissionToRole("store_manager", "users.can.invite");

        String invitedEmail = "invited-" + UUID.randomUUID() + "@example.com";
        var res = exchange(HttpMethod.POST, "/api/admin/user/invite", adminToken, Map.of(
                "email", invitedEmail,
                "role", "store_manager"
        ));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer tokenRows = jdbc.queryForObject(
                "select count(*) from access_tokens where type = 3 and content->>'email' = ? and content->>'role' = 'store_manager'",
                Integer.class,
                invitedEmail
        );
        assertThat(tokenRows).isEqualTo(1);
        var tokenValue = jdbc.queryForObject(
                "select token from access_tokens where type = 3 and content->>'email' = ? limit 1",
                String.class,
                invitedEmail
        );
        verify(emailDeliveryService).sendText(
                eq(invitedEmail),
                eq("You have been invited to Shoptopus Admin"),
                contains(adminInviteMailService.inviteUrl(tokenValue))
        );
    }

    @Test
    void cannot_invite_system_user_when_user_does_not_have_permission() {
        String email = "invite-user-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var res = exchange(HttpMethod.POST, "/api/admin/user/invite", token, Map.of(
                "email", "x-" + UUID.randomUUID() + "@example.com",
                "role", "store_manager"
        ));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invite_validation_works_on_duplicate_email() {
        String existingEmail = "existing-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(existingEmail);

        String adminEmail = "invite-admin2-" + UUID.randomUUID() + "@example.com";
        String adminToken = registerAndLogin(adminEmail);
        assignRole(userIdByEmail(adminEmail), "super_admin");

        var res = exchange(HttpMethod.POST, "/api/admin/user/invite", adminToken, Map.of(
                "email", existingEmail,
                "role", "store_manager"
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void user_can_register_by_invitation() {
        String adminEmail = "issuer-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(adminEmail);
        UUID adminId = userIdByEmail(adminEmail);

        String token = UUID.randomUUID().toString();
        String invitedEmail = "invite-register-" + UUID.randomUUID() + "@example.com";
        insertInviteToken(adminId, token, invitedEmail, "store_assistant", OffsetDateTime.now().plusDays(1), 3);

        var res = restTemplate.postForEntity("/api/auth/register-by-invite/" + token, Map.of(
                "first_name", "John",
                "last_name", "Smith",
                "password", "MyStrongPassword01$",
                "password_confirmation", "MyStrongPassword01$"
        ), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer users = jdbc.queryForObject("select count(*) from users where lower(email)=lower(?)", Integer.class, invitedEmail);
        assertThat(users).isEqualTo(1);
        var secondUse = restTemplate.postForEntity("/api/auth/register-by-invite/" + token, Map.of(
                "first_name", "John",
                "last_name", "Smith",
                "password", "MyStrongPassword01$",
                "password_confirmation", "MyStrongPassword01$"
        ), Map.class);
        assertThat(secondUse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondUse.getBody().get("developer_message")).isEqualTo("Invalid or expired token.");
    }

    @Test
    void register_by_invite_fails_if_access_token_is_expired_or_wrong_type() {
        String adminEmail = "issuer2-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(adminEmail);
        UUID adminId = userIdByEmail(adminEmail);
        String invitedEmail = "invite-fail-" + UUID.randomUUID() + "@example.com";

        String expiredToken = UUID.randomUUID().toString();
        insertInviteToken(adminId, expiredToken, invitedEmail, "store_assistant", OffsetDateTime.now().minusMinutes(1), 3);

        var expired = restTemplate.postForEntity("/api/auth/register-by-invite/" + expiredToken, Map.of(
                "first_name", "John",
                "last_name", "Smith",
                "password", "MyStrongPassword01$",
                "password_confirmation", "MyStrongPassword01$"
        ), Map.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(expired.getBody().get("developer_message")).isEqualTo("Invalid or expired token.");

        String wrongTypeToken = UUID.randomUUID().toString();
        insertInviteToken(adminId, wrongTypeToken, invitedEmail, "store_assistant", OffsetDateTime.now().plusDays(1), 2);
        var wrongType = restTemplate.postForEntity("/api/auth/register-by-invite/" + wrongTypeToken, Map.of(
                "first_name", "John",
                "last_name", "Smith",
                "password", "MyStrongPassword01$",
                "password_confirmation", "MyStrongPassword01$"
        ), Map.class);
        assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongType.getBody().get("developer_message")).isEqualTo("Invalid or expired token.");
    }

    @Test
    void register_by_invite_new_user_has_correct_role() {
        String adminEmail = "issuer3-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(adminEmail);
        UUID adminId = userIdByEmail(adminEmail);

        jdbc.update("insert into roles(name, created_at, updated_at) values ('store_assistant', now(), now()) on conflict (name) do nothing");
        String token = UUID.randomUUID().toString();
        String invitedEmail = "invite-role-" + UUID.randomUUID() + "@example.com";
        insertInviteToken(adminId, token, invitedEmail, "store_assistant", OffsetDateTime.now().plusDays(1), 3);

        var res = restTemplate.postForEntity("/api/auth/register-by-invite/" + token, Map.of(
                "first_name", "John",
                "last_name", "Smith",
                "password", "MyStrongPassword01$",
                "password_confirmation", "MyStrongPassword01$"
        ), Map.class);
        assertThat(res.getStatusCode())
                .as("body=%s", res.getBody())
                .isEqualTo(HttpStatus.OK);

        UUID newUserId = userIdByEmail(invitedEmail);
        Integer roleRows = jdbc.queryForObject(
                "select count(*) from model_has_roles mr join roles r on r.id = mr.role_id where mr.user_id = ? and r.name = 'store_assistant'",
                Integer.class,
                newUserId
        );
        assertThat(roleRows).isEqualTo(1);
    }

    private void assignRole(UUID userId, String roleName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?) on conflict do nothing", roleId, userId);
    }

    private void assignPermissionToRole(String roleName, String permissionName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        jdbc.update("insert into permissions(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", permissionName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        Integer permissionId = jdbc.queryForObject("select id from permissions where name = ? limit 1", Integer.class, permissionName);
        jdbc.update("insert into role_has_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", roleId, permissionId);
    }

    private void insertInviteToken(UUID issuerUserId, String token, String email, String role, OffsetDateTime expiry, int type) {
        jdbc.update(
                "insert into access_tokens(id, type, token, accessable_type, accessable_id, user_id, issuer_user_id, expiry, content, created_at, updated_at) " +
                        "values (?::uuid, ?, ?, 'User', 'NEW USER', ?::uuid, ?::uuid, ?, ?::jsonb, now(), now())",
                UUID.randomUUID().toString(),
                type,
                token,
                issuerUserId.toString(),
                issuerUserId.toString(),
                expiry,
                "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"
        );
    }
}

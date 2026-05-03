package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAndPermissionControllerTest extends AdminControllerTestBase {

    @Test
    void roles_permissions_and_role_relations_can_be_retrieved() {
        String token = token();
        Long storeManagerRole = seedRole("store_manager");
        Long superAdminRole = seedRole("super_admin");
        Long invitePermission = seedPermission("users.can.invite");
        Long updatePermission = seedPermission("users.can.update");
        jdbc.update("insert into role_has_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", superAdminRole, invitePermission);
        jdbc.update("insert into role_has_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", superAdminRole, updatePermission);

        String managerEmail = "storemanager-" + UUID.randomUUID() + "@example.com";
        UUID managerId = registerUser(managerEmail);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?) on conflict do nothing", storeManagerRole, managerId);

        var roles = authed(token, HttpMethod.GET, "/api/admin/roles", null);
        assert2xx(roles);
        assertThat((List<?>) roles.getBody().get("data")).isNotEmpty();

        var permissions = authed(token, HttpMethod.GET, "/api/admin/permissions", null);
        assert2xx(permissions);
        assertThat(((List<Map<?, ?>>) permissions.getBody().get("data"))).extracting(item -> String.valueOf(item.get("name"))).contains("users.can.invite");

        var rolePermissions = authed(token, HttpMethod.GET, "/api/admin/role/super_admin/permissions", null);
        assert2xx(rolePermissions);
        assertThat(((List<Map<?, ?>>) ((Map<?, ?>) rolePermissions.getBody().get("data")).get("data")))
                .extracting(item -> String.valueOf(item.get("name")))
                .contains("users.can.invite", "users.can.update");

        var roleUsers = authed(token, HttpMethod.GET, "/api/admin/role/store_manager/users", null);
        assert2xx(roleUsers);
        assertThat(((List<Map<?, ?>>) ((Map<?, ?>) roleUsers.getBody().get("data")).get("data")))
                .extracting(item -> String.valueOf(item.get("email")))
                .contains(managerEmail);

        var availableUsers = authed(token, HttpMethod.GET, "/api/admin/role/store_manager/available-users", null);
        assert2xx(availableUsers);
        assertThat(((List<Map<?, ?>>) ((Map<?, ?>) availableUsers.getBody().get("data")).get("data")))
                .extracting(item -> String.valueOf(item.get("email")))
                .doesNotContain(managerEmail);
    }

    @Test
    void role_can_be_created_updated_permission_assigned_user_assigned_and_deleted() {
        String token = token();

        var createRole = authed(token, HttpMethod.POST, "/api/admin/role", Map.of("name", "qa_role_" + UUID.randomUUID().toString().substring(0, 8)));
        assert2xx(createRole);
        Long roleId = Long.valueOf(((Map<?, ?>) createRole.getBody().get("data")).get("id").toString());
        assertThat(roleId).isNotNull();

        assert2xx(authed(token, HttpMethod.PATCH, "/api/admin/role/" + roleId, Map.of("name", "qa_role_updated_" + roleId)));
        String updatedName = jdbc.queryForObject("select name from roles where id = ?", String.class, roleId);
        assertThat(updatedName).startsWith("qa_role_updated_");

        jdbc.update("""
                insert into permissions(name, created_at, updated_at)
                values ('qa_permission', now(), now())
                on conflict (name) do nothing
                """);
        Long permissionId = jdbc.queryForObject("select id from permissions where name = 'qa_permission' limit 1", Long.class);
        assert2xx(authed(token, HttpMethod.POST, "/api/admin/role/" + roleId + "/permission/" + permissionId, null));
        Integer hasPerm = jdbc.queryForObject("select count(*) from role_has_permissions where role_id = ? and permission_id = ?", Integer.class, roleId, permissionId);
        assertThat(hasPerm).isEqualTo(1);

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/role/" + roleId + "/permission/" + permissionId, null));
        hasPerm = jdbc.queryForObject("select count(*) from role_has_permissions where role_id = ? and permission_id = ?", Integer.class, roleId, permissionId);
        assertThat(hasPerm).isZero();

        UUID userId = UUID.fromString(jdbc.queryForObject("select id::text from users where deleted_at is null limit 1", String.class));
        assert2xx(authed(token, HttpMethod.PATCH, "/api/admin/role/" + roleId + "/users/" + userId, null));
        Integer hasRole = jdbc.queryForObject("select count(*) from model_has_roles where role_id = ? and user_id = ?", Integer.class, roleId, userId);
        assertThat(hasRole).isEqualTo(1);

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/role/" + roleId + "/users/" + userId, null));
        hasRole = jdbc.queryForObject("select count(*) from model_has_roles where role_id = ? and user_id = ?", Integer.class, roleId, userId);
        assertThat(hasRole).isZero();

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/role/" + roleId, null));
        Integer roleCount = jdbc.queryForObject("select count(*) from roles where id = ?", Integer.class, roleId);
        assertThat(roleCount).isZero();
    }

    @Test
    void role_creation_validation_and_authorization_are_enforced() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/role", Map.of());
        assertValidationFailed(response, "name field is required");

        String email = "role-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Role",
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
        String customerToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(customerToken, HttpMethod.GET, "/api/admin/roles", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(customerToken, HttpMethod.GET, "/api/admin/permissions", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(customerToken, HttpMethod.POST, "/api/admin/role", Map.of("name", "blocked_role")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Long seedRole(String name) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", name);
        return jdbc.queryForObject("select id from roles where name = ? limit 1", Long.class, name);
    }

    private Long seedPermission(String name) {
        jdbc.update("insert into permissions(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", name);
        return jdbc.queryForObject("select id from permissions where name = ? limit 1", Long.class, name);
    }

    private UUID registerUser(String email) {
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "System",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        return UUID.fromString(jdbc.queryForObject("select id::text from users where email = ? limit 1", String.class, email));
    }
}

package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionAuthorizationTest extends AdminControllerTestBase {

    @Test
    void user_with_granted_permissions_can_access_completed_admin_slices_without_super_admin_role() {
        String email = "perms-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Perm",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "store_manager");
        assignPermissionToRole("store_manager", "voucher_codes.view");
        assignPermissionToRole("store_manager", "voucher_codes.manage");
        assignPermissionToRole("store_manager", "payments.manage");
        assignPermissionToRole("store_manager", "product_categories.view");
        assignPermissionToRole("store_manager", "product_categories.manage");
        assignPermissionToRole("store_manager", "product_tags.view");
        assignPermissionToRole("store_manager", "product_tags.manage");
        assignPermissionToRole("store_manager", "product_attributes.view");
        assignPermissionToRole("store_manager", "product_attributes.manage");
        assignPermissionToRole("store_manager", "products.view");
        assignPermissionToRole("store_manager", "products.manage");
        assignPermissionToRole("store_manager", "reports.view");
        assignPermissionToRole("store_manager", "emails.manage");
        assignPermissionToRole("store_manager", "notifications.view");
        assignPermissionToRole("store_manager", "notifications.manage");
        assignPermissionToRole("store_manager", "ai.manage");
        assignPermissionToRole("store_manager", "delivery_types.view");
        assignPermissionToRole("store_manager", "delivery_types.manage");
        assignPermissionToRole("store_manager", "files.view");
        assignPermissionToRole("store_manager", "files.manage");
        assignPermissionToRole("store_manager", "notes.view");
        assignPermissionToRole("store_manager", "notes.manage");
        assignPermissionToRole("store_manager", "audits.view");
        assignPermissionToRole("store_manager", "roles.view");
        assignPermissionToRole("store_manager", "users.can.invite");
        assignPermissionToRole("store_manager", "users.view");
        assignPermissionToRole("store_manager", "users.manage");
        assignPermissionToRole("store_manager", "customers.view");
        assignPermissionToRole("store_manager", "carts.view");
        assignPermissionToRole("store_manager", "carts.manage");

        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/voucher-codes", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/payments", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/product-categories", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/product-tags", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/product-attributes", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/products", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/dashboard", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/get-users", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product-category", Map.of(
                "name", Map.of("en", "Permitted category"),
                "description", Map.of("en", "Permitted description")
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product-tag", Map.of(
                "name", Map.of("en", "Permitted tag"),
                "description", Map.of("en", "Permitted description")
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product-attribute", Map.of(
                "name", Map.of("en", "Permitted attribute"),
                "type", 0
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product", Map.of(
                "name", Map.of("en", "Permitted product"),
                "short_description", Map.of("en", "Short"),
                "description", Map.of("en", "Long"),
                "price", 10,
                "stock", 1
        )).getStatusCode().is2xxSuccessful()).isTrue();
        String managedUserId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, email);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/send-email", Map.of(
                "subject", "Permitted subject",
                "message", "Permitted message",
                "user_ids", java.util.List.of(managedUserId)
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/notifications", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/ai/translations", Map.of(
                "text", "hello"
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/payment", Map.of(
                "payable_type", "App\\Models\\Order",
                "payable_id", UUID.randomUUID().toString(),
                "amount", "11.00"
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/voucher-code", Map.of(
                "amount", 10,
                "type", 1,
                "valid_from", "2026-01-01T00:00:00Z",
                "valid_until", "2026-01-07T00:00:00Z"
        )).getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(authed(token, HttpMethod.GET, "/api/admin/delivery-types", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/delivery-type", Map.of(
                "name", "{\"en\":\"Perm Delivery\"}",
                "price", 10
        )).getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(authed(token, HttpMethod.GET, "/api/admin/files", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/file", Map.of("name", "perm.txt")).getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(authed(token, HttpMethod.GET, "/api/admin/notes", null).getStatusCode().is2xxSuccessful()).isTrue();
        String userId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, email);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/note", Map.of(
                "user_id", userId,
                "noteable_type", "user",
                "noteable_id", userId,
                "text", "hello"
        )).getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(authed(token, HttpMethod.GET, "/api/admin/audits", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/roles", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/users", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/customers", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.GET, "/api/admin/carts", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/cart", Map.of(
                "slug", "perm-cart-" + UUID.randomUUID()
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/user", Map.of(
                "first_name", "Permitted",
                "last_name", "Manager",
                "email", "managed-" + UUID.randomUUID() + "@example.com"
        )).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/user/invite", Map.of(
                "email", "invited-" + UUID.randomUUID() + "@example.com",
                "role", "store_manager"
        )).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void user_without_matching_permission_is_forbidden_even_if_authenticated() {
        String email = "perms-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Blocked",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "store_manager");
        assignPermissionToRole("store_manager", "files.view");
        assignPermissionToRole("store_manager", "users.view");

        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/files", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authed(token, HttpMethod.GET, "/api/admin/users", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/user", Map.of(
                "first_name", "Blocked",
                "last_name", "User",
                "email", "blocked-" + UUID.randomUUID() + "@example.com"
        )).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/file", Map.of("name", "blocked.txt")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.GET, "/api/admin/roles", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest extends AdminControllerTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void users_must_be_invited_but_existing_users_can_still_be_updated_and_deleted() {
        String token = token();
        String email = "crud-user-" + UUID.randomUUID() + "@example.com";

        var create = authed(token, HttpMethod.POST, "/api/admin/user", Map.of(
                "first_name", "Test",
                "last_name", "User",
                "email", email
        ));
        assertThat(create.getStatusCode().is4xxClientError()).isTrue();

        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Test",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow();

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/user/" + user.getId(), Map.of("first_name", "Updated")));
        user = userRepository.findById(user.getId()).orElseThrow();
        assertThat(user.getFirstName()).isEqualTo("Updated");

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/user/" + user.getId(), null));
        user = userRepository.findById(user.getId()).orElseThrow();
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void users_can_be_listed_and_shown() {
        String token = token();
        String email = "list-user-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "List",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow();

        var list = authed(token, HttpMethod.GET, "/api/admin/users", null);
        assert2xx(list);
        Map<?, ?> row = ((java.util.List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> user.getId().toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("email")).isEqualTo(email);

        var show = authed(token, HttpMethod.GET, "/api/admin/user/" + user.getId(), null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(user.getId().toString());
        assertThat(data.get("first_name")).isEqualTo("List");
    }

    @Test
    void users_require_users_permissions() {
        String email = "users-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Users",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "user_manager");
        assignPermissionToRole("user_manager", "users.view");
        assignPermissionToRole("user_manager", "users.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/users", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/user", Map.of(
                "first_name", "Allowed",
                "last_name", "User",
                "email", "allowed-" + UUID.randomUUID() + "@example.com"
        )).getStatusCode().is4xxClientError()).isTrue();
        assignPermissionToRole("user_manager", "users.can.invite");
        assertThat(authed(token, HttpMethod.POST, "/api/admin/user/invite", Map.of(
                "email", "allowed-" + UUID.randomUUID() + "@example.com",
                "role", "user_manager"
        )).getStatusCode().is2xxSuccessful()).isTrue();
    }
}

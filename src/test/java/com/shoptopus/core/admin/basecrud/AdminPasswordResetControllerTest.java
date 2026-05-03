package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AdminPasswordResetControllerTest extends AdminControllerTestBase {

    @Test
    void admin_password_reset_routes_use_the_same_single_use_token_flow() {
        String email = rootUserUsername;

        var forgot = restTemplate.postForEntity("/api/admin-ui/auth/forgot-password", Map.of("email", email), Map.class);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer tokenRows = jdbc.queryForObject(
                "select count(*) from password_reset_tokens where lower(email)=lower(?)",
                Integer.class,
                email
        );
        assertThat(tokenRows).isEqualTo(1);

        String resetToken = jdbc.queryForObject(
                "select token from password_reset_tokens where lower(email)=lower(?) order by created_at desc limit 1",
                String.class,
                email
        );

        var reset = restTemplate.postForEntity("/api/admin-ui/auth/reset-password", Map.of(
                "email", email,
                "token", resetToken,
                "password", "newpassword123",
                "password_confirmation", "newpassword123"
        ), Map.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer tokenRowsAfterReset = jdbc.queryForObject(
                "select count(*) from password_reset_tokens where lower(email)=lower(?)",
                Integer.class,
                email
        );
        assertThat(tokenRowsAfterReset).isEqualTo(0);

        var login = restTemplate.postForEntity("/api/admin-ui/auth/login", Map.of(
                "email", email,
                "password", "newpassword123"
        ), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void authenticated_admin_users_cannot_use_reset_routes() {
        String token = token();

        var forgot = authed(token, HttpMethod.POST, "/api/admin-ui/auth/forgot-password", Map.of("email", "ignored@example.com"));
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var reset = authed(token, HttpMethod.POST, "/api/admin-ui/auth/reset-password", Map.of(
                "email", "ignored@example.com",
                "token", "x",
                "password", "password123",
                "password_confirmation", "password123"
        ));
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

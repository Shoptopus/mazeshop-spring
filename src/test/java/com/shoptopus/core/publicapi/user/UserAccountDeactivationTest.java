package com.shoptopus.core.publicapi.user;

import com.shoptopus.core.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountDeactivationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void user_can_deactivate_own_account_without_anonimizing() {
        String email = "deactivate-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var res = exchange(HttpMethod.DELETE, "/api/user/account?anonimize=0", token, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("message")).isEqualTo("User account deleted");

        Integer active = jdbc.queryForObject(
                "select count(*) from users where lower(email)=lower(?) and deleted_at is null",
                Integer.class,
                email
        );
        assertThat(active).isEqualTo(0);

        String replacedEmail = jdbc.queryForObject(
                "select email from users where deleted_at is not null order by updated_at desc limit 1",
                String.class
        );
        assertThat(replacedEmail).contains("DEACTIVATED-");
    }

    @Test
    void user_can_deactivate_own_account_with_anonimizing() {
        String email = "anonimize-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var res = exchange(HttpMethod.DELETE, "/api/user/account?anonimize=1", token, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("message")).isEqualTo("User account deleted");

        String replacedEmail = jdbc.queryForObject(
                "select email from users where deleted_at is not null order by updated_at desc limit 1",
                String.class
        );
        assertThat(replacedEmail).doesNotContain("DEACTIVATED-");
        assertThat(replacedEmail).doesNotContain(email);
    }

    private String registerAndLogin(String email) {
        var register = restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Deactivate",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assertThat(register.getStatusCode().is2xxSuccessful()).isTrue();

        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }
}

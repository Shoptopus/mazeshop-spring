package com.shoptopus.core.storefront;

import com.shoptopus.core.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.storefront.social-providers=google,facebook")
class StorefrontAuthApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @Test
    void storefront_auth_routes_are_available() {
        String email = "storefront-auth-" + UUID.randomUUID() + "@example.com";

        var providers = exchange(HttpMethod.GET, "/api/storefront/auth/social-providers", null, null);
        assertThat(providers.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataList(providers)).hasSize(2);
        assertThat(dataList(providers).get(0)).containsKeys("provider", "label", "login_url", "authorize_url", "callback_url");

        var register = exchange(HttpMethod.POST, "/api/storefront/auth/register", Map.of(
                "first_name", "Store",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), null);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(castMap(dataObject(register).get("auth"))).containsEntry("authenticated", true)
                .containsKeys("token", "token_type", "user");

        var login = exchange(HttpMethod.POST, "/api/storefront/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> loginAuth = castMap(dataObject(login).get("auth"));
        assertThat(loginAuth).containsEntry("authenticated", true)
                .containsKeys("token", "token_type", "user");
        String token = String.valueOf(loginAuth.get("token"));

        var me = exchange(HttpMethod.GET, "/api/storefront/auth/me", null, token);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> meAuth = castMap(dataObject(me).get("auth"));
        assertThat(meAuth).containsEntry("authenticated", true)
                .containsEntry("token_type", "Bearer")
                .containsKey("user");

        var forgot = exchange(HttpMethod.POST, "/api/storefront/auth/forgot-password", Map.of("email", email), null);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.OK);

        var social = exchange(HttpMethod.GET, "/api/storefront/auth/social/google", null, null);
        assertThat(social.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataObject(social)).containsEntry("provider", "google")
                .containsKeys("target_url", "login_url", "authorize_url", "callback_url");

        seedGoogleProviderConfig();
        var socialAuthorize = exchange(HttpMethod.GET, "/api/storefront/auth/social/google/authorize", null, null);
        assertThat(socialAuthorize.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(socialAuthorize.getHeaders().getLocation()).isNotNull();
        assertThat(socialAuthorize.getHeaders().getLocation().toString()).contains("accounts.google.com");

        var invalidProvider = exchange(HttpMethod.GET, "/api/storefront/auth/social/github", null, null);
        assertThat(invalidProvider.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var logout = exchange(HttpMethod.POST, "/api/storefront/auth/logout", Map.of(), token);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(castMap(dataObject(logout).get("auth"))).containsEntry("authenticated", false);
    }

    @Test
    void stored_snake_case_google_config_authorizes_successfully() {
        seedGoogleProviderConfig();

        var response = exchange(HttpMethod.GET, "/api/storefront/auth/social/google/authorize", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
                .contains("accounts.google.com")
                .contains("client_id=401663361017-fij0phd36nal9qa4arho3aqrek0ih08p.apps.googleusercontent.com");
    }

    private void seedGoogleProviderConfig() {
        jdbcTemplate.update("delete from system_settings where key = ?", "storefront.social_provider_configs");
        jdbcTemplate.update(
                """
                        insert into system_settings (id, key, value, type, deleted_at, created_at, updated_at)
                        values (?::uuid, ?, ?, 0, null, now(), now())
                        """,
                UUID.randomUUID().toString(),
                "storefront.social_provider_configs",
                """
                        [{"provider":"google","label":"Google","enabled":true,"client_id":"401663361017-fij0phd36nal9qa4arho3aqrek0ih08p.apps.googleusercontent.com","client_secret":"secret","authorization_uri":"https://accounts.google.com/o/oauth2/v2/auth","token_uri":"https://oauth2.googleapis.com/token","user_info_uri":"https://openidconnect.googleapis.com/v1/userinfo","scopes":["openid","profile","email"],"user_id_attribute":"sub","email_attribute":"email","first_name_attribute":"given_name","last_name_attribute":"family_name","name_attribute":"name"}]
                        """
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataObject(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dataList(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange("http://127.0.0.1:" + port + path, method, entity, Map.class);
    }
}

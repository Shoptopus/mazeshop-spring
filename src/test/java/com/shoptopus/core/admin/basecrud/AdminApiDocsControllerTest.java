package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApiDocsControllerTest extends AdminControllerTestBase {

    @Test
    void openapi_docs_require_authentication_and_are_available_to_authenticated_admins() {
        var unauthenticated = restTemplate.getForEntity("/api/docs/openapi", Map.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthenticated.getBody()).isNotNull();
        assertThat(unauthenticated.getBody().get("error_code")).isEqualTo(1101);

        String token = token();
        var authenticated = authed(token, HttpMethod.GET, "/api/docs/openapi", null);
        assert2xx(authenticated);
        assertThat(authenticated.getBody()).isNotNull();
        assertThat(authenticated.getBody()).containsKeys("openapi", "paths", "components");
    }
}

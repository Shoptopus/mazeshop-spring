package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.IntegrationTestBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AdminControllerTestBase extends IntegrationTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    @Qualifier("logsDataSource")
    protected DataSource logsDataSource;

    @Value("${app.media.storage-path}")
    protected String mediaStoragePath;

    protected String token() {
        String email = "admin-basecrud-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Admin",
                "last_name", "BaseCrud",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        assignRole(email, "super_admin");
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    protected void assignRole(String email, String roleName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        String userId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, email);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?::uuid) on conflict do nothing", roleId, userId);
    }

    protected void assignPermissionToRole(String roleName, String permissionName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        jdbc.update("insert into permissions(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", permissionName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        Integer permissionId = jdbc.queryForObject("select id from permissions where name = ? limit 1", Integer.class, permissionName);
        jdbc.update("insert into role_has_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", roleId, permissionId);
    }

    protected ResponseEntity<Map> authed(String token, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    protected ResponseEntity<Map> authedMultipart(
            String token,
            String path,
            String fileField,
            byte[] bytes,
            String filename,
            String contentType,
            Map<String, String> fields
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource file = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(fileField, new HttpEntity<>(file, fileHeaders));
        if (fields != null) {
            fields.forEach(body::add);
        }

        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    protected Path mediaRoot() {
        return Path.of(mediaStoragePath).toAbsolutePath().normalize();
    }

    protected void assert2xx(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Expected 2xx, got " + response.getStatusCode() + " body=" + response.getBody())
                .isTrue();
    }

    protected void assertValidationFailed(ResponseEntity<Map> response, String expectedMessagePart) {
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("user_message")).isEqualTo("Validation failed");
        assertThat(String.valueOf(response.getBody().get("developer_message"))).contains(expectedMessagePart);
    }

    protected JdbcTemplate logsJdbc() {
        return new JdbcTemplate(logsDataSource);
    }
}

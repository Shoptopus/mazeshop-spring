package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FilesBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_delete_multiple_files() {
        String token = token();
        List<UUID> ids = List.of(seedFile(), seedFile(), seedFile());

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/files/bulk", Map.of("ids", ids)));
        assertThat(jdbc.queryForObject("select count(*) from file_contents where id = ?::uuid", Integer.class, ids.get(0).toString())).isEqualTo(0);
        assertThat(jdbc.queryForObject("select count(*) from file_contents where id = ?::uuid", Integer.class, ids.get(1).toString())).isEqualTo(0);
    }

    @Test
    void bulk_file_delete_validation_is_enforced() {
        String token = token();
        assertValidationFailed(authed(token, HttpMethod.DELETE, "/api/admin/files/bulk", Map.of()), "ids field is required");
        assertValidationFailed(authed(token, HttpMethod.DELETE, "/api/admin/files/bulk", Map.of("ids", List.of("invalid id"))), "selected ids.0 is invalid");
    }

    @Test
    void bulk_file_delete_requires_super_admin() {
        String token = customerToken("files");
        List<UUID> ids = List.of(seedFile());
        assertThat(authed(token, HttpMethod.DELETE, "/api/admin/files/bulk", Map.of("ids", ids)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedFile() {
        String id = UUID.randomUUID().toString();
        String path = "bulk-files/" + id + ".pdf";
        writeMediaFile(path);
        jdbc.update("""
                insert into file_contents(id, name, mime_type, size, path, slug, created_at, updated_at)
                values (?::uuid, 'Guide', 'application/pdf', 100, ?, ?, now(), now())
                """,
                id, path, "file-" + id);
        return UUID.fromString(id);
    }

    private void writeMediaFile(String path) {
        try {
            java.nio.file.Path target = mediaRoot().resolve(path).normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, "dummy");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String customerToken(String prefix) {
        String email = prefix + "-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", prefix,
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
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }
}

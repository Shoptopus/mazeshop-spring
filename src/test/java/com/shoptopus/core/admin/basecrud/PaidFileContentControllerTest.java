package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaidFileContentControllerTest extends AdminControllerTestBase {

    @Test
    void paid_file_content_can_be_created_listed_updated_downloaded_and_deleted() {
        String token = token();
        String productId = seedProduct();

        var create = authed(token, HttpMethod.POST, "/api/admin/product/" + productId + "/paid-content", Map.of(
                "name", "ebook.pdf",
                "path", "/files/ebook.pdf",
                "title", "Premium Ebook",
                "description", "Digital download"
        ));
        assert2xx(create);
        String paidFileContentId = String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id"));

        var list = authed(token, HttpMethod.GET, "/api/admin/product/" + productId + "/paid-contents", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) ((Map<?, ?>) list.getBody().get("data")).get("data")).stream()
                .filter(item -> paidFileContentId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("file_name")).isEqualTo("ebook.pdf");

        var update = authed(token, HttpMethod.POST, "/api/admin/product/" + productId + "/paid-content/" + paidFileContentId, Map.of(
                "name", "ebook-v2.pdf",
                "path", "/files/ebook-v2.pdf"
        ));
        assert2xx(update);
        assertThat(jdbc.queryForObject("select file_name from paid_file_contents where id = ?::uuid", String.class, paidFileContentId)).isEqualTo("ebook-v2.pdf");

        var download = authed(token, HttpMethod.GET, "/api/admin/product/" + productId + "/paid-content/" + paidFileContentId + "/download", null);
        assert2xx(download);
        Map<?, ?> downloadData = (Map<?, ?>) download.getBody().get("data");
        assertThat(downloadData.get("url")).isEqualTo("/files/ebook-v2.pdf");

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/product/" + productId + "/paid-content/" + paidFileContentId, null);
        assert2xx(delete);
        assertThat(jdbc.queryForObject("select count(*) from paid_file_contents where id = ?::uuid", Integer.class, paidFileContentId)).isEqualTo(0);
    }

    @Test
    void paid_file_content_validation_and_permissions_are_enforced() {
        String token = token();
        String productId = seedProduct();

        var missingName = authed(token, HttpMethod.POST, "/api/admin/product/" + productId + "/paid-content", Map.of(
                "path", "/files/ebook.pdf"
        ));
        assertValidationFailed(missingName, "original_file_name field is required");

        String email = "paid-content-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Paid",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "catalog_manager");
        assignPermissionToRole("catalog_manager", "products.view");
        assignPermissionToRole("catalog_manager", "products.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String permittedToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(permittedToken, HttpMethod.GET, "/api/admin/product/" + productId + "/paid-contents", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        String blockedEmail = "paid-content-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Paid",
                "last_name", "Blocked",
                "email", blockedEmail,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(blockedEmail, "customer");
        var blockedLogin = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", blockedEmail,
                "password", "password123"
        ), Map.class);
        String blockedToken = (String) ((Map<?, ?>) ((Map<?, ?>) blockedLogin.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(blockedToken, HttpMethod.GET, "/api/admin/product/" + productId + "/paid-contents", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String seedProduct() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into products(id, name, slug, short_description, description, price, stock, virtual, status, purchase_count, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, 10, 4, false, 0, 0, now(), now())
                """,
                id, "{\"en\":\"Paid product\"}", "paid-product-" + id, "{\"en\":\"Short\"}", "{\"en\":\"Description\"}");
        return id;
    }
}

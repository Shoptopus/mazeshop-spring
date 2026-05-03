package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.catalog.ProductTag;
import com.shoptopus.core.catalog.repository.ProductTagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTagControllerTest extends AdminControllerTestBase {

    @Autowired
    private ProductTagRepository tagRepository;

    @Test
    void test_product_tag_can_be_created_updated_and_deleted() {
        String token = token();

        var create = authed(token, HttpMethod.POST, "/api/admin/product-tag", Map.of(
                "name", Map.of("en", "Tag"),
                "description", Map.of("en", "Tag desc"),
                "slug", "tag-" + UUID.randomUUID()
        ));
        assert2xx(create);
        UUID id = UUID.fromString(((Map<?, ?>) create.getBody().get("data")).get("id").toString());

        var list = authed(token, HttpMethod.GET, "/api/admin/product-tags", null);
        assert2xx(list);
        Map<?, ?> listed = ((java.util.List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> id.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(listed.get("name")).isEqualTo(Map.of("en", "Tag"));

        var show = authed(token, HttpMethod.GET, "/api/admin/product-tag/" + id, null);
        assert2xx(show);
        Map<?, ?> showData = (Map<?, ?>) show.getBody().get("data");
        assertThat(showData.get("id")).isEqualTo(id.toString());
        assertThat(showData.get("description")).isEqualTo(Map.of("en", "Tag desc"));

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/product-tag/" + id, Map.of("enabled", false)));
        ProductTag tag = tagRepository.findById(id).orElseThrow();
        assertThat(tag.isEnabled()).isFalse();

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/product-tag/" + id, null));
        tag = tagRepository.findById(id).orElseThrow();
        assertThat(tag.getDeletedAt()).isNotNull();
    }

    @Test
    void test_product_tag_creation_validation() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/product-tag", Map.of("description", Map.of("en", "desc")));
        assertValidationFailed(response, "name field is required");
    }

    @Test
    void product_tag_requires_permissions() {
        String email = "tag-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Tag",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "catalog_manager");
        assignPermissionToRole("catalog_manager", "product_tags.view");
        assignPermissionToRole("catalog_manager", "product_tags.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/product-tags", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product-tag", Map.of(
                "name", Map.of("en", "Allowed tag"),
                "description", Map.of("en", "Allowed description")
        )).getStatusCode().is2xxSuccessful()).isTrue();
    }
}

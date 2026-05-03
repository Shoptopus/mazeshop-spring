package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.repository.ProductCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategoryControllerTest extends AdminControllerTestBase {

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Test
    void test_product_category_can_be_created_updated_and_deleted() {
        String token = token();

        var create = authed(token, HttpMethod.POST, "/api/admin/product-category", Map.of(
                "name", Map.of("en", "Category"),
                "description", Map.of("en", "Category Description"),
                "slug", "category-" + UUID.randomUUID()
        ));
        assert2xx(create);
        UUID id = UUID.fromString(((Map<?, ?>) create.getBody().get("data")).get("id").toString());

        var list = authed(token, HttpMethod.GET, "/api/admin/product-categories", null);
        assert2xx(list);
        Map<?, ?> listed = ((java.util.List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> id.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(listed.get("name")).isEqualTo(Map.of("en", "Category"));

        var show = authed(token, HttpMethod.GET, "/api/admin/product-category/" + id, null);
        assert2xx(show);
        Map<?, ?> showData = (Map<?, ?>) show.getBody().get("data");
        assertThat(showData.get("id")).isEqualTo(id.toString());
        assertThat(showData.get("description")).isEqualTo(Map.of("en", "Category Description"));

        var select = authed(token, HttpMethod.GET, "/api/admin/product-categories/select-data", null);
        assert2xx(select);
        assertThat(((Map<?, ?>) select.getBody().get("data")).get("data")).isInstanceOf(java.util.List.class);

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/product-category/" + id, Map.of("enabled", false)));
        ProductCategory category = categoryRepository.findById(id).orElseThrow();
        assertThat(category.isEnabled()).isFalse();

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/product-category/" + id, null));
        category = categoryRepository.findById(id).orElseThrow();
        assertThat(category.getDeletedAt()).isNotNull();
    }

    @Test
    void test_product_category_create_validation() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/product-category", Map.of("description", Map.of("en", "desc")));
        assertValidationFailed(response, "name field is required");
    }

    @Test
    void product_categories_can_be_imported_from_nested_tree_payloads() {
        String token = token();

        ProductCategory existing = new ProductCategory();
        existing.setId(UUID.randomUUID());
        existing.setName(new LinkedHashMap<>(Map.of("en", "Accessories", "de", "Zubehoer")));
        existing.setDescription(new LinkedHashMap<>(Map.of("en", "Existing description")));
        existing.setSlug("accessories");
        existing.setEnabled(false);
        categoryRepository.save(existing);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Accessories", Map.of(
                "Belts & Braces", Map.of(
                        "Belts", Map.of()
                )
        ));

        var response = authed(token, HttpMethod.POST, "/api/admin/product-categories/import", payload);
        assert2xx(response);

        ProductCategory root = categoryRepository.findBySlugAndDeletedAtIsNull("accessories").orElseThrow();
        ProductCategory child = categoryRepository.findBySlugAndDeletedAtIsNull("accessories-belts-braces").orElseThrow();
        ProductCategory grandchild = categoryRepository.findBySlugAndDeletedAtIsNull("accessories-belts-braces-belts").orElseThrow();

        assertThat(root.getName()).containsEntry("en", "Accessories").containsEntry("de", "Zubehoer");
        assertThat(root.isEnabled()).isTrue();
        assertThat(child.getParent().getId()).isEqualTo(root.getId());
        assertThat(grandchild.getParent().getId()).isEqualTo(child.getId());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsEntry("imported", 3).containsEntry("created", 2).containsEntry("updated", 1);
    }

    @Test
    void product_category_import_validates_entire_tree_before_writing() {
        String token = token();

        Map<String, Object> duplicateSlugPayload = new LinkedHashMap<>();
        duplicateSlugPayload.put("A!", Map.of());
        duplicateSlugPayload.put("A@", Map.of());

        var duplicateSlug = authed(token, HttpMethod.POST, "/api/admin/product-categories/import", duplicateSlugPayload);
        assertValidationFailed(duplicateSlug, "generated slug \"a\" is duplicated");
        assertThat(categoryRepository.findBySlugAndDeletedAtIsNull("a")).isEmpty();

        var invalidChildren = authed(token, HttpMethod.POST, "/api/admin/product-categories/import", Map.of("Accessories", "invalid"));
        assertValidationFailed(invalidChildren, "must map to an object of child categories");
        assertThat(categoryRepository.findBySlugAndDeletedAtIsNull("accessories")).isEmpty();
    }

    @Test
    void product_category_requires_permissions() {
        String email = "category-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Category",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "catalog_manager");
        assignPermissionToRole("catalog_manager", "product_categories.view");
        assignPermissionToRole("catalog_manager", "product_categories.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/product-categories", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product-category", Map.of(
                "name", Map.of("en", "Allowed category"),
                "description", Map.of("en", "Allowed description")
        )).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product-categories/import", Map.of("Imported", Map.of())).getStatusCode().is2xxSuccessful()).isTrue();
    }
}

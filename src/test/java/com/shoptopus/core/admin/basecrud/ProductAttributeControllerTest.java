package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAttributeControllerTest extends AdminControllerTestBase {

    @Autowired
    private ProductAttributeRepository attributeRepository;
    @Autowired
    private ProductAttributeOptionRepository optionRepository;

    @Test
    void test_product_attribute_and_option_can_be_created_updated_and_deleted() {
        String token = token();

        var create = authed(token, HttpMethod.POST, "/api/admin/product-attribute", Map.of(
                "name", Map.of("en", "Color"),
                "slug", "attribute-" + UUID.randomUUID(),
                "type", 0,
                "show_in_filters", false
        ));
        assert2xx(create);
        UUID attributeId = UUID.fromString(((Map<?, ?>) create.getBody().get("data")).get("id").toString());

        var list = authed(token, HttpMethod.GET, "/api/admin/product-attributes", null);
        assert2xx(list);
        Map<?, ?> listed = ((java.util.List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> attributeId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(listed.get("name")).isEqualTo(Map.of("en", "Color"));

        var show = authed(token, HttpMethod.GET, "/api/admin/product-attribute/" + attributeId, null);
        assert2xx(show);
        Map<?, ?> showData = (Map<?, ?>) show.getBody().get("data");
        assertThat(showData.get("id")).isEqualTo(attributeId.toString());
        assertThat(showData.get("type")).isEqualTo(0);
        assertThat(showData.get("show_in_filters")).isEqualTo(false);

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/product-attribute/" + attributeId, Map.of("enabled", false, "show_in_filters", true)));
        ProductAttribute attribute = attributeRepository.findById(attributeId).orElseThrow();
        assertThat(attribute.isEnabled()).isFalse();
        assertThat(attribute.isShowInFilters()).isTrue();

        var optionCreate = authed(token, HttpMethod.POST, "/api/admin/product-attribute/" + attributeId + "/option", Map.of(
                "name", Map.of("en", "Red"),
                "slug", "option-" + UUID.randomUUID(),
                "value", "red"
        ));
        assert2xx(optionCreate);
        UUID optionId = UUID.fromString(((Map<?, ?>) optionCreate.getBody().get("data")).get("id").toString());

        var options = authed(token, HttpMethod.GET, "/api/admin/product-attribute/" + attributeId + "/options", null);
        assert2xx(options);
        assertThat(((Map<?, ?>) options.getBody().get("data")).get("data")).isInstanceOf(java.util.List.class);

        var optionShow = authed(token, HttpMethod.GET, "/api/admin/product-attribute/" + attributeId + "/option/" + optionId, null);
        assert2xx(optionShow);
        Map<?, ?> optionData = (Map<?, ?>) optionShow.getBody().get("data");
        assertThat(optionData.get("value")).isEqualTo("red");

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/product-attribute/" + attributeId + "/option/" + optionId, Map.of("value", "blue")));
        ProductAttributeOption option = optionRepository.findById(optionId).orElseThrow();
        assertThat(option.getValue()).isEqualTo("blue");

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/product-attribute/" + attributeId + "/option/" + optionId, null));
        option = optionRepository.findById(optionId).orElseThrow();
        assertThat(option.getDeletedAt()).isNotNull();

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/product-attribute/" + attributeId, null));
        attribute = attributeRepository.findById(attributeId).orElseThrow();
        assertThat(attribute.getDeletedAt()).isNotNull();
    }

    @Test
    void test_product_attribute_creation_validation() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/product-attribute", Map.of("type", 0));
        assertValidationFailed(response, "name field is required");
    }

    @Test
    void test_product_attribute_option_creation_validation() {
        String token = token();
        ProductAttribute attribute = new ProductAttribute();
        attribute.setName(Map.of("en", "Size"));
        attribute.setSlug("size-" + UUID.randomUUID());
        attributeRepository.save(attribute);

        var response = authed(token, HttpMethod.POST, "/api/admin/product-attribute/" + attribute.getId() + "/option", Map.of("value", "XL"));
        assertValidationFailed(response, "name field is required");
    }

    @Test
    void product_attribute_requires_permissions() {
        String email = "attribute-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Attribute",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "catalog_manager");
        assignPermissionToRole("catalog_manager", "product_attributes.view");
        assignPermissionToRole("catalog_manager", "product_attributes.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/product-attributes", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product-attribute", Map.of(
                "name", Map.of("en", "Allowed attribute"),
                "type", 0
        )).getStatusCode().is2xxSuccessful()).isTrue();
    }
}

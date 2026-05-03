package com.shoptopus.core.publicapi.productattribute;

import com.shoptopus.core.publicapi.catalog.CatalogPublicApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetAllProductAttributesTest extends CatalogPublicApiTestBase {

    @Test
    void it_returns_the_correct_format() {
        var res = exchange(HttpMethod.GET, "/api/product_attributes", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsKeys("message", "data", "records", "total_records");
    }

    @Test
    void it_returns_all_required_fields_and_associated_options() {
        UUID attrId = createAttribute("Size", true);
        createAttributeOption(attrId, "Large", true);
        createAttributeOption(attrId, "Medium", true);

        var res = exchange(HttpMethod.GET, "/api/product_attributes", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var row = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .filter(r -> attrId.toString().equals(String.valueOf(r.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.keySet()).contains("id", "name", "slug", "type", "image", "options");
        assertThat((List<?>) row.get("options")).hasSize(2);
    }

    @Test
    void soft_deleted_and_disabled_attributes_are_not_returned() {
        UUID deletedId = createAttribute("Deleted Attribute", true);
        createAttributeOption(deletedId, "A", true);
        jdbc.update("update product_attributes set deleted_at = now() where id = ?::uuid", deletedId.toString());

        UUID disabledId = createAttribute("Disabled Attribute", false);
        createAttributeOption(disabledId, "B", true);

        var res = exchange(HttpMethod.GET, "/api/product_attributes", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ids = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .map(r -> String.valueOf(r.get("id")))
                .toList();
        assertThat(ids).doesNotContain(deletedId.toString(), disabledId.toString());
    }

    @Test
    void options_exclude_disabled_or_soft_deleted_rows() {
        UUID attrId = createAttribute("Color", true);
        UUID enabledOption = createAttributeOption(attrId, "Red", true);
        UUID disabledOption = createAttributeOption(attrId, "Blue", false);
        UUID deletedOption = createAttributeOption(attrId, "Green", true);
        jdbc.update("update product_attribute_options set deleted_at = now() where id = ?::uuid", deletedOption.toString());

        var res = exchange(HttpMethod.GET, "/api/product_attributes", null, null);
        var row = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .filter(r -> attrId.toString().equals(String.valueOf(r.get("id"))))
                .findFirst()
                .orElseThrow();

        var optionIds = ((List<Map<String, Object>>) row.get("options")).stream()
                .map(o -> String.valueOf(o.get("id")))
                .toList();
        assertThat(optionIds).contains(enabledOption.toString());
        assertThat(optionIds).doesNotContain(disabledOption.toString(), deletedOption.toString());
    }

    @Test
    void product_category_specific_endpoint_is_reachable_and_returns_same_shape() {
        createAttributeOption(createAttribute("Material", true), "Cotton", true);
        UUID anyCategory = createCategory("Any Category", true);

        var res = exchange(HttpMethod.GET, "/api/product_attributes/product_category/" + anyCategory, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsKeys("message", "data", "records", "total_records");
    }
}

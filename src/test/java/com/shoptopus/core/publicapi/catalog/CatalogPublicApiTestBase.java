package com.shoptopus.core.publicapi.catalog;

import com.shoptopus.core.IntegrationTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

public abstract class CatalogPublicApiTestBase extends IntegrationTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbc;

    protected ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    protected UUID createProduct(String name) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace(" ", "-") + "-" + id;
        jdbc.update(
                "insert into products(id, name, slug, short_description, description, price, status, purchase_count, stock, virtual, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, 0, 0, 100, false, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                slug,
                "{\"en\":\"Short\"}",
                "{\"en\":\"Description\"}",
                19.99
        );
        return id;
    }

    protected UUID createCategory(String name, boolean enabled) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace(" ", "-") + "-" + id;
        jdbc.update(
                "insert into product_categories(id, parent_id, name, slug, description, enabled, created_at, updated_at) " +
                        "values (?::uuid, null, ?::jsonb, ?, ?::jsonb, ?, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                slug,
                "{\"en\":\"Description\"}",
                enabled
        );
        return id;
    }

    protected UUID createTag(String name, boolean enabled) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace(" ", "-") + "-" + id;
        jdbc.update(
                "insert into product_tags(id, name, slug, description, display_badge, enabled, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, ?::jsonb, false, ?, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                slug,
                "{\"en\":\"Description\"}",
                enabled
        );
        return id;
    }

    protected UUID createAttribute(String name, boolean enabled) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace(" ", "-") + "-" + id;
        jdbc.update(
                "insert into product_attributes(id, name, slug, type, image, enabled, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, 0, '{}'::jsonb, ?, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                slug,
                enabled
        );
        return id;
    }

    protected UUID createAttributeOption(UUID attributeId, String name, boolean enabled) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace(" ", "-") + "-" + id;
        jdbc.update(
                "insert into product_attribute_options(id, name, slug, value, product_attribute_id, image, enabled, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, ?, ?::uuid, '{}'::jsonb, ?, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                slug,
                name,
                attributeId.toString(),
                enabled
        );
        return id;
    }

    protected void attachProductCategory(UUID productId, UUID categoryId) {
        jdbc.update(
                "insert into product_product_category(product_id, product_category_id) values (?::uuid, ?::uuid)",
                productId.toString(),
                categoryId.toString()
        );
    }

    protected void attachProductTag(UUID productId, UUID tagId) {
        jdbc.update(
                "insert into product_product_tag(product_id, product_tag_id) values (?::uuid, ?::uuid)",
                productId.toString(),
                tagId.toString()
        );
    }
}

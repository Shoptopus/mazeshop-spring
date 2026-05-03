package com.shoptopus.core.unit;

import com.shoptopus.core.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTypeTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void it_has_name_slug_description_and_enabled_fields() {
        UUID typeId = UUID.randomUUID();
        jdbc.update(
                "insert into delivery_types(id, name, slug, description, enabled, created_at, updated_at) values (?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())",
                typeId.toString(),
                "{\"en\":\"Express\"}",
                "express-" + typeId,
                "{\"en\":\"Express delivery\"}"
        );

        String name = jdbc.queryForObject("select name->>'en' from delivery_types where id = ?::uuid", String.class, typeId.toString());
        String slug = jdbc.queryForObject("select slug from delivery_types where id = ?::uuid", String.class, typeId.toString());
        String description = jdbc.queryForObject("select description->>'en' from delivery_types where id = ?::uuid", String.class, typeId.toString());
        Boolean enabled = jdbc.queryForObject("select enabled from delivery_types where id = ?::uuid", Boolean.class, typeId.toString());

        assertThat(name).isEqualTo("Express");
        assertThat(slug).startsWith("express-");
        assertThat(description).isEqualTo("Express delivery");
        assertThat(enabled).isTrue();
    }

    @Test
    void it_may_have_many_delivery_rules_and_orders_reference() {
        UUID typeId = UUID.randomUUID();
        jdbc.update(
                "insert into delivery_types(id, name, slug, description, enabled, created_at, updated_at) values (?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())",
                typeId.toString(),
                "{\"en\":\"Scheduled\"}",
                "scheduled-" + typeId,
                "{\"en\":\"Scheduled delivery\"}"
        );

        jdbc.update(
                "insert into delivery_rules(id, delivery_type_id, name, slug, country, price, enabled, created_at, updated_at) " +
                        "values (?::uuid, ?::uuid, ?::jsonb, ?, ?, ?, true, now(), now())",
                UUID.randomUUID().toString(),
                typeId.toString(),
                "{\"en\":\"Rule A\"}",
                "rule-a-" + UUID.randomUUID(),
                "GB",
                0
        );
        jdbc.update(
                "insert into delivery_rules(id, delivery_type_id, name, slug, country, price, enabled, created_at, updated_at) " +
                        "values (?::uuid, ?::uuid, ?::jsonb, ?, ?, ?, true, now(), now())",
                UUID.randomUUID().toString(),
                typeId.toString(),
                "{\"en\":\"Rule B\"}",
                "rule-b-" + UUID.randomUUID(),
                "GB",
                0
        );

        Integer rulesCount = jdbc.queryForObject(
                "select count(*) from delivery_rules where delivery_type_id = ?::uuid and deleted_at is null",
                Integer.class,
                typeId.toString()
        );
        assertThat(rulesCount).isEqualTo(2);

        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        jdbc.update(
                "insert into users(id, first_name, last_name, email, temporary, is_favorite, slug, created_at, updated_at) values (?::uuid, ?, ?, ?, false, false, ?, now(), now())",
                userId.toString(), "Order", "User", "order-user-" + UUID.randomUUID() + "@example.com", "user-" + UUID.randomUUID()
        );
        jdbc.update(
                "insert into addresses(id, address_line_1, town, post_code, slug, user_id, country, created_at, updated_at) values (?::uuid, ?, ?, ?, ?, ?::uuid, ?, now(), now())",
                addressId.toString(), "1 Delivery St", "London", "E1 1AA", "address-" + UUID.randomUUID(), userId.toString(), "UK"
        );
        jdbc.update(
                "insert into orders(id, address_id, user_id, delivery_type_id, original_price, subtotal, total_price, total_discount, delivery_cost, currency_code, status, slug, created_at, updated_at) " +
                        "values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 10, 10, 10, 0, 0, 'GBP', 0, ?, now(), now())",
                orderId.toString(), addressId.toString(), userId.toString(), typeId.toString(), "order-" + UUID.randomUUID()
        );

        String orderDeliveryType = jdbc.queryForObject("select delivery_type_id::text from orders where id = ?::uuid", String.class, orderId.toString());
        assertThat(orderDeliveryType).isEqualTo(typeId.toString());
    }
}

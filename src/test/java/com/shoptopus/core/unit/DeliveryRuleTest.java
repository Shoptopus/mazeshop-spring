package com.shoptopus.core.unit;

import com.shoptopus.core.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRuleTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void it_has_expected_fields_and_belongs_to_delivery_type() {
        UUID typeId = UUID.randomUUID();
        jdbc.update(
                "insert into delivery_types(id, name, slug, description, enabled, created_at, updated_at) values (?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())",
                typeId.toString(),
                "{\"en\":\"Standard\"}",
                "standard-" + typeId,
                "{\"en\":\"Standard delivery\"}"
        );

        UUID ruleId = UUID.randomUUID();
        jdbc.update(
                "insert into delivery_rules(id, delivery_type_id, name, slug, country, min_cart_price, max_cart_price, price, enabled, created_at, updated_at) " +
                        "values (?::uuid, ?::uuid, ?::jsonb, ?, ?, ?, ?, ?, true, now(), now())",
                ruleId.toString(),
                typeId.toString(),
                "{\"en\":\"Rule 1\"}",
                "rule-1-" + ruleId,
                "GB",
                10.00,
                100.00,
                0
        );

        String country = jdbc.queryForObject("select country from delivery_rules where id = ?::uuid", String.class, ruleId.toString());
        String relation = jdbc.queryForObject("select delivery_type_id::text from delivery_rules where id = ?::uuid", String.class, ruleId.toString());
        Boolean enabled = jdbc.queryForObject("select enabled from delivery_rules where id = ?::uuid", Boolean.class, ruleId.toString());

        assertThat(country).isEqualTo("GB");
        assertThat(relation).isEqualTo(typeId.toString());
        assertThat(enabled).isTrue();
    }
}

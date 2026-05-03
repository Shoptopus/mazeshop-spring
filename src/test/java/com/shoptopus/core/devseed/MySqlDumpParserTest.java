package com.shoptopus.core.devseed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDumpParserTest {

    private final MySqlDumpParser parser = new MySqlDumpParser();

    @Test
    void parses_create_and_insert_statements_with_json_nulls_and_escaped_quotes() {
        Map<String, MySqlDumpParser.ParsedTable> tables = parser.parseStatements(List.of(
                "CREATE TABLE `users` (",
                "  `id` char(36) NOT NULL,",
                "  `email` varchar(255) NOT NULL,",
                "  `avatar` json DEFAULT NULL,",
                "  `created_at` timestamp NULL DEFAULT NULL",
                ");",
                "INSERT INTO `users` VALUES ('abc-123','user@example.com','{\\\"file_name\\\":\\\"avatar1.jpg\\\"}','2026-01-21 11:33:40'),('def-456','quote@example.com',NULL,'2026-01-21 11:33:41');"
        ));

        assertThat(tables).containsKey("users");
        assertThat(tables.get("users").columns()).containsExactly("id", "email", "avatar", "created_at");
        assertThat(tables.get("users").rows()).hasSize(2);

        Map<String, String> first = tables.get("users").mappedRows().get(0);
        assertThat(first.get("id")).isEqualTo("abc-123");
        assertThat(first.get("email")).isEqualTo("user@example.com");
        assertThat(first.get("avatar")).isEqualTo("{\"file_name\":\"avatar1.jpg\"}");
        assertThat(first.get("created_at")).isEqualTo("2026-01-21 11:33:40");

        Map<String, String> second = tables.get("users").mappedRows().get(1);
        assertThat(second.get("avatar")).isNull();
    }
}

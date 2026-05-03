package com.shoptopus.core.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class RootUserBootstrap implements ApplicationRunner {

    private static final UUID ROOT_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final JdbcTemplate jdbcTemplate;
    private final RootUserProperties properties;

    public RootUserBootstrap(JdbcTemplate jdbcTemplate, RootUserProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer rootUserCount = jdbcTemplate.queryForObject(
                "select count(*) from users where id = ?::uuid",
                Integer.class,
                ROOT_USER_ID.toString()
        );
        if (rootUserCount == null || rootUserCount == 0) {
            return;
        }

        String configuredUsername = normalize(properties.getUsername());
        String currentUsername = jdbcTemplate.queryForObject(
                "select lower(trim(email)) from users where id = ?::uuid",
                String.class,
                ROOT_USER_ID.toString()
        );
        currentUsername = normalize(currentUsername);
        if (configuredUsername.equals(currentUsername)) {
            return;
        }

        Integer conflictingUsers = jdbcTemplate.queryForObject(
                """
                select count(*)
                from users
                where lower(email) = lower(?)
                  and deleted_at is null
                  and id <> ?::uuid
                """,
                Integer.class,
                configuredUsername,
                ROOT_USER_ID.toString()
        );
        if (conflictingUsers != null && conflictingUsers > 0) {
            throw new IllegalStateException("Configured app.root-user.username is already used by another account: " + configuredUsername);
        }

        jdbcTemplate.update(
                "update users set email = ?, updated_at = now() where id = ?::uuid",
                configuredUsername,
                ROOT_USER_ID.toString()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            throw new IllegalStateException("app.root-user.username must not be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalStateException("app.root-user.username must not be blank");
        }
        return normalized;
    }
}

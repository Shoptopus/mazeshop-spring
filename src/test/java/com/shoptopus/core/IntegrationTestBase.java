package com.shoptopus.core;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

@SpringBootTest(
        classes = CoreApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    private static final Path TEST_MEDIA_ROOT =
            Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "shoptopus-spring-test-media",
                    UUID.randomUUID().toString());

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("shoptopus")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Value("${app.root-user.username}")
    protected String rootUserUsername;

    @BeforeAll
    static void setup() {
        POSTGRES.start();
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE shoptopus_logs");
        } catch (Exception ignored) {
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.main.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.main.username", POSTGRES::getUsername);
        registry.add("spring.datasource.main.password", POSTGRES::getPassword);

        registry.add(
                "spring.datasource.logs.url",
                () -> POSTGRES.getJdbcUrl().replace("/shoptopus", "/shoptopus_logs"));
        registry.add("spring.datasource.logs.username", POSTGRES::getUsername);
        registry.add("spring.datasource.logs.password", POSTGRES::getPassword);

        registry.add("app.errors.include-developer-details", () -> "true");
        registry.add("app.mail.outbox.enabled", () -> "false");
        registry.add("app.checkout.recovery.enabled", () -> "false");
        registry.add("app.jwt.secret", () -> "test-secret-test-secret-test-secret-32");
        registry.add("app.media.storage-path", () -> TEST_MEDIA_ROOT.toString());
    }
}

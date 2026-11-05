package com.shoptopus.core;

import com.shoptopus.core.config.RootUserBootstrap;
import com.shoptopus.core.config.RootUserProperties;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional("mainTransactionManager")
class RootUserBootstrapTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void startup_updates_seeded_root_user_username_from_configuration() {
        String configuredUsername = "owner-" + UUID.randomUUID() + "@example.com";
        RootUserProperties properties = new RootUserProperties();
        properties.setUsername("  " + configuredUsername.toUpperCase(Locale.ROOT) + "  ");

        // Exercise the startup runner inside a rolled-back transaction, not shared context startup.
        new RootUserBootstrap(jdbc, properties).run(new DefaultApplicationArguments());

        assertThat(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(configuredUsername)).isPresent();
        assertThat(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(rootUserUsername)).isEmpty();
    }
}

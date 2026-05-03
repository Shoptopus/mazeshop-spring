package com.shoptopus.core;

import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.root-user.username=owner@example.com")
class RootUserBootstrapTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void startup_updates_seeded_root_user_username_from_configuration() {
        assertThat(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("owner@example.com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("superadmin@m.com")).isEmpty();
    }
}

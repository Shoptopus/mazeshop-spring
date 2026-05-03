package com.shoptopus.core.unit;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.auth.AccessToken;
import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenTest extends IntegrationTestBase {

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void it_has_a_type_field() {
        var token = persistToken();
        assertThat(token.getType()).isEqualTo(3);
    }

    @Test
    void it_has_a_token_field() {
        var token = persistToken();
        assertThat(token.getToken()).startsWith("token-");
    }

    @Test
    void it_belongs_to_a_user() {
        var token = persistToken();
        var loaded = accessTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(loaded.getUser().getId()).isNotNull();
    }

    @Test
    void it_belongs_to_an_issuer() {
        var token = persistToken();
        var loaded = accessTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(loaded.getIssuerUser().getId()).isNotNull();
    }

    private AccessToken persistToken() {
        User user = createUser("access-user");
        User issuer = createUser("access-issuer");
        AccessToken token = new AccessToken();
        token.setType(3);
        token.setToken("token-" + UUID.randomUUID());
        token.setAccessableType("User");
        token.setAccessableId(user.getId().toString());
        token.setUser(user);
        token.setIssuerUser(issuer);
        token.setContent(Map.of("email", user.getEmail()));
        return accessTokenRepository.save(token);
    }

    private User createUser(String prefix) {
        User user = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setFirstName(prefix);
        user.setLastName("test");
        user.setName(prefix + " test");
        user.setEmail(prefix + "-" + suffix + "@example.com");
        user.setSlug("user-" + UUID.randomUUID());
        user.setTemporary(false);
        return userRepository.save(user);
    }
}

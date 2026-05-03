package com.shoptopus.core.auth.security;

import com.shoptopus.core.auth.AccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {
    Optional<AccessToken> findByToken(String token);
    Optional<AccessToken> findFirstByTypeAndAccessableTypeAndAccessableIdOrderByCreatedAtDesc(Integer type, String accessableType, String accessableId);
    void deleteByToken(String token);
}

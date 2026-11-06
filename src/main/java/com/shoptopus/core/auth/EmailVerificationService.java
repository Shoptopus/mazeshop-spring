package com.shoptopus.core.auth;

import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.mail.SystemMailOutbox;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class EmailVerificationService {
    private static final int TYPE = 10;
    private static final String KIND = "EmailVerification";
    private final AccessTokenRepository tokens;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final SystemMailOutbox outbox;
    private final String storefront;

    public EmailVerificationService(
            AccessTokenRepository tokens,
            UserRepository users,
            JdbcTemplate jdbc,
            SystemMailOutbox outbox,
            @Value("${app.storefront.public-base-url:http://localhost:8080}") String storefront) {
        this.tokens = tokens;
        this.users = users;
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.storefront = storefront;
    }

    @Transactional("mainTransactionManager")
    public void send(User user) {
        if (user.getEmailVerifiedAt() != null) return;
        jdbc.queryForList("select id from users where id=? for update", user.getId());
        var previous =
                tokens.findFirstByTypeAndAccessableTypeAndAccessableIdOrderByCreatedAtDesc(
                        TYPE, KIND, user.getId().toString());
        if (previous.filter(t -> t.getCreatedAt().isAfter(OffsetDateTime.now().minusSeconds(60)))
                .isPresent()) return;
        String raw = UUID.randomUUID().toString() + UUID.randomUUID();
        AccessToken token = new AccessToken();
        token.setType(TYPE);
        token.setAccessableType(KIND);
        token.setAccessableId(user.getId().toString());
        token.setUser(user);
        token.setToken(SystemMailOutbox.digest(raw));
        token.setExpiry(OffsetDateTime.now().plusHours(24));
        token.setContent(Map.of("used", false));
        tokens.save(token);
        String url = storefront.replaceAll("/+$", "") + "/verify-email?token=" + raw;
        outbox.enqueue(
                user.getEmail(),
                "Confirm your email address",
                "<h1>Confirm your email</h1><p>Thanks for creating your account. Confirm your email"
                        + " address to keep your account details up to date.</p><p><a href=\""
                        + url
                        + "\">Confirm email address</a></p><p>This link expires in 24 hours. You"
                        + " can continue shopping while you confirm your email. If you did not"
                        + " create this account, ignore this email.</p>");
    }

    public void sendPasswordReset(User user, String token) {
        String url =
                storefront.replaceAll("/+$", "")
                        + "/reset-password?token="
                        + token
                        + "&amp;email="
                        + java.net.URLEncoder.encode(
                                user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        outbox.enqueue(
                user.getEmail(),
                "Reset your Mazeshop password",
                "<h1>Reset your password</h1><p><a href=\""
                        + url
                        + "\">Choose a new password</a></p><p>This single-use link expires in 60"
                        + " minutes. If you did not request a password reset, ignore this"
                        + " email.</p>");
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> verify(String raw) {
        if (raw == null || raw.isBlank()) throw invalid();
        var token = tokens.findByToken(SystemMailOutbox.digest(raw)).orElseThrow(this::invalid);
        if (token.getType() != TYPE || !KIND.equals(token.getAccessableType())) throw invalid();
        jdbc.queryForList(
                "select id from users where id=?::uuid for update", token.getAccessableId());
        if (token.getExpiry() == null || token.getExpiry().isBefore(OffsetDateTime.now()))
            throw new ApiException(
                    HttpStatus.GONE,
                    "This confirmation link has expired. Request a new one.",
                    "Email verification token expired");
        var user =
                users.findById(UUID.fromString(token.getAccessableId()))
                        .filter(u -> u.getDeletedAt() == null)
                        .orElseThrow(this::invalid);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(OffsetDateTime.now());
            users.save(user);
        }
        token.setContent(Map.of("used", true));
        tokens.save(token);
        return Map.of("verified", true, "message", "Your email address is confirmed.");
    }

    private ApiException invalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "This confirmation link is invalid. Request a new one.",
                "Invalid email verification token");
    }
}

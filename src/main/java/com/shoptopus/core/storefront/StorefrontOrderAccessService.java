package com.shoptopus.core.storefront;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.ordering.Order;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class StorefrontOrderAccessService {
    private final JdbcTemplate jdbc;
    private final com.shoptopus.core.ordering.repository.OrderRepository orders;

    public StorefrontOrderAccessService(
            JdbcTemplate jdbc, com.shoptopus.core.ordering.repository.OrderRepository orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    @org.springframework.transaction.annotation.Transactional(
            readOnly = true,
            transactionManager = "mainTransactionManager")
    public void requireAccess(java.util.UUID id, String email, String token) {
        requireAccess(
                orders.findByIdAndDeletedAtIsNull(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Order not found.",
                                                "Order not found")),
                email,
                token);
    }

    public void requireAccess(Order order, String authenticatedEmail, String token) {
        if (order.getUser() != null
                && authenticatedEmail != null
                && authenticatedEmail.equalsIgnoreCase(order.getUser().getEmail())) return;
        var tokens =
                jdbc.queryForList(
                        "select access_token from storefront_checkout_attempts where order_id=? and"
                            + " created_at>now()-interval '30 days'",
                        String.class,
                        order.getId());
        if (token != null
                && tokens.stream()
                        .anyMatch(
                                t ->
                                        MessageDigest.isEqual(
                                                t.getBytes(StandardCharsets.UTF_8),
                                                token.getBytes(StandardCharsets.UTF_8)))) return;
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Open this order from your confirmation email or sign in to its account.",
                "Order owner or scoped guest access required");
    }
}

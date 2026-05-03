package com.shoptopus.core.cart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("ip_address") String ipAddress,
        Object user,
        List<CartItemResponse> products
) {
}

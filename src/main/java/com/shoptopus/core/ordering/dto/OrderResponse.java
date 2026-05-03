package com.shoptopus.core.ordering.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("address_id") UUID addressId,
        @JsonProperty("total_price") BigDecimal totalPrice,
        BigDecimal subtotal,
        @JsonProperty("total_discount") BigDecimal totalDiscount,
        Integer status,
        @JsonProperty("currency_code") String currencyCode
) {
}

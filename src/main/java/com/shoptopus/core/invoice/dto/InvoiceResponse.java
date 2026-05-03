package com.shoptopus.core.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("order_id") UUID orderId,
        Map<String, Object> address,
        Map<String, Object> payment,
        Map<String, Object> products,
        @JsonProperty("voucher_code") Map<String, Object> voucherCode,
        @JsonProperty("delivery_type") Map<String, Object> deliveryType,
        Map<String, Object> totals,
        boolean valid,
        String slug
) {
}

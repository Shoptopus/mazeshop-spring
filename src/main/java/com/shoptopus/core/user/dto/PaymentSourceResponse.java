package com.shoptopus.core.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

public record PaymentSourceResponse(
        UUID id,
        String provider,
        String brand,
        String last4,
        @JsonProperty("is_default") boolean isDefault,
        Map<String, Object> metadata
) {
}

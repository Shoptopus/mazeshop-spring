package com.shoptopus.core.payment.dto;

import java.util.Map;

public record PaymentProviderSettingsResponse(
        String provider,
        boolean enabled,
        Map<String, String> settings
) {
}

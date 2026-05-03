package com.shoptopus.core.settings.dto;

import java.util.UUID;

public record SystemSettingResponse(
        UUID id,
        String key,
        String value,
        Integer type
) {
}

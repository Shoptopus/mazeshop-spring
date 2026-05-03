package com.shoptopus.core.catalog.dto;

import java.util.Map;
import java.util.UUID;

public record ProductAttributeOptionResponse(
        UUID id,
        String name,
        String slug,
        String value,
        Map<String, Object> image
) {
}

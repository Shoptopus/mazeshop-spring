package com.shoptopus.core.catalog.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductAttributeResponse(
        UUID id,
        String name,
        String slug,
        Integer type,
        Map<String, Object> image,
        List<ProductAttributeOptionResponse> options
) {
}

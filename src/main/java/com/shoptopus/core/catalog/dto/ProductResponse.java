package com.shoptopus.core.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal finalPrice,
        Long stock,
        String sku,
        Set<UUID> categoryIds,
        Set<UUID> tagIds,
        Map<String, Object> coverImage,
        List<Map<String, Object>> contents
) {
}

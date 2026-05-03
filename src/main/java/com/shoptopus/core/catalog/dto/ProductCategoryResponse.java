package com.shoptopus.core.catalog.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductCategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        String description,
        Map<String, Object> menuImage,
        Map<String, Object> headerImage,
        List<Map<String, Object>> contents
) {
}

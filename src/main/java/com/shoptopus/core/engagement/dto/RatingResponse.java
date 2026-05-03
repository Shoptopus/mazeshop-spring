package com.shoptopus.core.engagement.dto;

import java.util.Map;
import java.util.UUID;

public record RatingResponse(
        UUID id,
        Integer rating,
        String title,
        String description,
        String languagePrefix,
        Boolean verified,
        String ratableType,
        UUID ratableId,
        Long reviewCount,
        Double averageRating,
        Map<String, Object> image,
        Map<String, Object> video
) {
}

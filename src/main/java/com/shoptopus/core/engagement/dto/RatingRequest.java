package com.shoptopus.core.engagement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RatingRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        String title,
        @JsonAlias({"description", "content"}) String description,
        @JsonAlias("language_prefix") String languagePrefix,
        Map<String, Object> image,
        Map<String, Object> video
) {
}

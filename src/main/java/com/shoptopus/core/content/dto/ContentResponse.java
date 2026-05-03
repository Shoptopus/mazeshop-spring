package com.shoptopus.core.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ContentResponse(
        UUID id,
        @JsonProperty("contentable_type") String contentableType,
        @JsonProperty("contentable_id") UUID contentableId,
        @JsonProperty("language_id") Long languageId,
        Integer type,
        String text
) {
}

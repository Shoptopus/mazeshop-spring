package com.shoptopus.core.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record ProductUpsertRequest(
        @Valid @NotNull TranslationInput name,
        @NotBlank String slug,
        @Valid @NotNull TranslationInput shortDescription,
        @Valid @NotNull TranslationInput description,
        @NotNull @DecimalMin("0") BigDecimal price,
        @Valid TranslationInput headline,
        @Valid TranslationInput subtitle,
        Long stock,
        String sku,
        Set<UUID> categoryIds,
        Set<UUID> tagIds
) {
}

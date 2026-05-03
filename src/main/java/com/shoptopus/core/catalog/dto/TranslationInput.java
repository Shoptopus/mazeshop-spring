package com.shoptopus.core.catalog.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record TranslationInput(
        String text,
        Map<String, String> i18n,
        @NotBlank String fallbackLanguage
) {
}

package com.shoptopus.core.io.dto;

public record CategoryImportRow(
        String slug,
        String nameEn,
        String descriptionEn,
        Boolean enabled
) {
}

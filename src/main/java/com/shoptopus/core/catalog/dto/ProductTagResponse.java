package com.shoptopus.core.catalog.dto;

import java.util.UUID;

public record ProductTagResponse(
        UUID id,
        String name,
        String slug,
        String description
) {
}

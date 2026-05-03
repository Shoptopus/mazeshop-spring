package com.shoptopus.core.catalog.dto;

public record ProductFilterRequest(
        String search,
        String categoryId,
        Double minPrice,
        Double maxPrice,
        Integer page,
        Integer size
) {
}

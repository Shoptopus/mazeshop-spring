package com.shoptopus.core.cart.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateCartQuantityRequest(
        @JsonAlias("product_id") @NotNull UUID productId,
        @JsonAlias("product_variant_id") UUID productVariantId,
        @NotNull @Min(1) Long quantity
) {
}

package com.shoptopus.core.cart.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddCartItemRequest(
        @JsonAlias("cart_id") UUID cartId,
        @JsonAlias("product_id") @NotNull UUID productId,
        @JsonAlias("product_variant_id") UUID productVariantId,
        @JsonAlias("customization_value") String customizationValue,
        @NotNull @Min(1) Long quantity
) {
}

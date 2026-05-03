package com.shoptopus.core.cart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("product_variant_id") UUID productVariantId,
        String name,
        @JsonProperty("item_original_price") BigDecimal itemOriginalPrice,
        @JsonProperty("item_final_price") BigDecimal itemFinalPrice,
        @JsonProperty("subtotal_original_price") BigDecimal subtotalOriginalPrice,
        @JsonProperty("subtotal_final_price") BigDecimal subtotalFinalPrice,
        @JsonProperty("customization_value") String customizationValue,
        Long quantity,
        @JsonProperty("remaining_stock") Long remainingStock,
        @JsonProperty("in_other_carts") Long inOtherCarts
) {
}

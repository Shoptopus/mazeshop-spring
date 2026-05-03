package com.shoptopus.core.cart.repository;

import com.shoptopus.core.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartId(UUID cartId);
    @Query("""
            select item
            from CartItem item
            where item.cart.id = :cartId
              and item.product.id = :productId
              and ((:productVariantId is null and item.productVariant is null) or item.productVariant.id = :productVariantId)
              and coalesce(item.customizationValue, '') = coalesce(:customizationValue, '')
            """)
    Optional<CartItem> findLineItem(
            @Param("cartId") UUID cartId,
            @Param("productId") UUID productId,
            @Param("productVariantId") UUID productVariantId,
            @Param("customizationValue") String customizationValue
    );
}

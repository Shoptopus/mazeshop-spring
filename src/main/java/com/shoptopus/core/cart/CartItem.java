package com.shoptopus.core.cart;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.common.jpa.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cart_product")
public class CartItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @Column(name = "customization_value")
    private String customizationValue;

    @Column(nullable = false)
    private Long quantity = 1L;
}

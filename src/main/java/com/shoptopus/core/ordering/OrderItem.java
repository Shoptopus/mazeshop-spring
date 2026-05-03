package com.shoptopus.core.ordering;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.common.jpa.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "order_product")
public class OrderItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @Column(nullable = false)
    private Integer amount = 1;

    @Column(nullable = false)
    private String name;

    @Column(name = "original_unit_price", nullable = false)
    private BigDecimal originalUnitPrice = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "full_price", nullable = false)
    private BigDecimal fullPrice = BigDecimal.ZERO;

    @Column(name = "final_price", nullable = false)
    private BigDecimal finalPrice = BigDecimal.ZERO;

    @Column(name = "unit_discount", nullable = false)
    private BigDecimal unitDiscount = BigDecimal.ZERO;

    @Column(name = "total_discount", nullable = false)
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean returned;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(nullable = false)
    private String slug;

    @Column(name = "customization_value")
    private String customizationValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> urls = new HashMap<>();
}

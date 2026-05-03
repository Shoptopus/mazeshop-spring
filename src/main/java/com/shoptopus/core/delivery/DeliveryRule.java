package com.shoptopus.core.delivery;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "delivery_rules")
public class DeliveryRule extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "delivery_type_id", nullable = false)
    private DeliveryType deliveryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "country")
    private String country;

    @Column(name = "min_cart_price", precision = 18, scale = 2)
    private BigDecimal minCartPrice;

    @Column(name = "max_cart_price", precision = 18, scale = 2)
    private BigDecimal maxCartPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "postcodes", columnDefinition = "jsonb", nullable = false)
    private List<String> postcodes = new ArrayList<>();

    @Column(name = "min_weight")
    private Integer minWeight;

    @Column(name = "max_weight")
    private Integer maxWeight;

    @Column(name = "min_distance", precision = 10, scale = 2)
    private BigDecimal minDistance;

    @Column(name = "max_distance", precision = 10, scale = 2)
    private BigDecimal maxDistance;

    @Column(name = "lat", precision = 10, scale = 6)
    private BigDecimal lat;

    @Column(name = "lon", precision = 10, scale = 6)
    private BigDecimal lon;

    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}

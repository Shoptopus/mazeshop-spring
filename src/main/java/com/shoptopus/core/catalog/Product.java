package com.shoptopus.core.catalog;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> name = new HashMap<>();

    @Column(nullable = false)
    private String slug;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "short_description", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> shortDescription = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> description = new HashMap<>();

    @Column(nullable = false)
    private boolean customizable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "customization_text", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> customizationText = new HashMap<>();

    @Column(nullable = false)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> headline = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> subtitle = new HashMap<>();

    @Column(nullable = false)
    private Integer status = 0;

    @Column(name = "purchase_count", nullable = false)
    private Long purchaseCount = 0L;

    @Column(nullable = false)
    private Long favourites = 0L;

    @Column(nullable = false)
    private Long stock = 0L;

    @Column(name = "backup_stock")
    private Long backupStock = 0L;

    @Column(unique = true, length = 50)
    private String sku;

    @Column(nullable = false)
    private boolean virtual;

    @Column
    private Integer weight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cover_photo", columnDefinition = "jsonb")
    private Map<String, Object> coverPhoto = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "available_attribute_options", columnDefinition = "jsonb")
    private Map<String, Object> availableAttributeOptions = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_tags", columnDefinition = "jsonb", nullable = false)
    private List<String> metaTags = new ArrayList<>();

    @Column
    private Double rating;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "product_product_category",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "product_category_id")
    )
    private Set<ProductCategory> categories = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "product_product_tag",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "product_tag_id")
    )
    private Set<ProductTag> tags = new HashSet<>();
}

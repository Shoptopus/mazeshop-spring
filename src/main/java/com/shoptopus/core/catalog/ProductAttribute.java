package com.shoptopus.core.catalog;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.store.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "product_attributes")
public class ProductAttribute extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> name = new HashMap<>();

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private Integer type = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> image = new HashMap<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "show_in_filters", nullable = false)
    private boolean showInFilters = true;

    @OneToMany(mappedBy = "productAttribute", fetch = FetchType.LAZY)
    private List<ProductAttributeOption> options = new ArrayList<>();
}

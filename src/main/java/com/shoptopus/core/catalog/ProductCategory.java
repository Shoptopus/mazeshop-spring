package com.shoptopus.core.catalog;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "product_categories")
public class ProductCategory extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> name = new HashMap<>();

    @Column(nullable = false)
    private String slug;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> description = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "menu_image", columnDefinition = "jsonb")
    private Map<String, Object> menuImage = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "header_image", columnDefinition = "jsonb")
    private Map<String, Object> headerImage = new HashMap<>();

    @Column(nullable = false)
    private boolean enabled = true;
}

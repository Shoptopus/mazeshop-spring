package com.shoptopus.core.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "product_variants")
public class ProductVariant extends SoftDeleteEntity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(columnDefinition = "text")
    private String data;

    @Column(nullable = false)
    private Long stock = 0L;

    @Column(unique = true, length = 50)
    private String sku;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> description = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_options", columnDefinition = "jsonb")
    private JsonNode attributeOptions;

    @Column(nullable = false)
    private boolean enabled = true;

    @SuppressWarnings("unchecked")
    public Map<String, Object> attributeOptionsAsMap() {
        if (attributeOptions != null && attributeOptions.isObject()) {
            return OBJECT_MAPPER.convertValue(attributeOptions, Map.class);
        }
        return new HashMap<>();
    }

    public List<Object> attributeOptionsAsList() {
        if (attributeOptions != null && attributeOptions.isArray()) {
            return OBJECT_MAPPER.convertValue(attributeOptions, List.class);
        }
        if (attributeOptions != null && attributeOptions.isObject()) {
            Map<String, Object> map = OBJECT_MAPPER.convertValue(attributeOptions, Map.class);
            return List.copyOf(map.values());
        }
        return List.of();
    }

    public void setAttributeOptions(Object value) {
        if (value == null) {
            this.attributeOptions = null;
            return;
        }
        this.attributeOptions = OBJECT_MAPPER.valueToTree(value);
    }
}

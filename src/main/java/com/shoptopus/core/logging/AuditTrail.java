package com.shoptopus.core.logging;

import com.shoptopus.core.common.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "audits")
public class AuditTrail extends AuditableEntity {

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "operation", nullable = false, length = 32)
    private String operation;

    @Column(name = "entity_type", nullable = false, length = 160)
    private String entityType;

    @Column(name = "entity_id", length = 160)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private Map<String, Object> beforeState = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private Map<String, Object> afterState = new LinkedHashMap<>();
}

package com.shoptopus.core.content;

import com.shoptopus.core.common.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "contents")
public class Content extends AuditableEntity {

    @Column(name = "contentable_type")
    private String contentableType;

    @Column(name = "contentable_id")
    private UUID contentableId;

    @Column(name = "language_id", nullable = false)
    private Long languageId;

    @Column(nullable = false)
    private Integer type;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data = new HashMap<>();

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}

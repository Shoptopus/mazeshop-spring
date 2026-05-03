package com.shoptopus.core.store;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import com.shoptopus.core.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "stores")
public class Store extends SoftDeleteEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "business_details", columnDefinition = "text")
    private String businessDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> logo = new LinkedHashMap<>();

    @Column(length = 255)
    private String tagline;

    @Column(name = "contact_phone", length = 80)
    private String contactPhone;

    @Column(name = "contact_address", columnDefinition = "text")
    private String contactAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_platforms", columnDefinition = "jsonb")
    private java.util.List<Map<String, Object>> socialPlatforms = new java.util.ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme", columnDefinition = "jsonb")
    private Map<String, Object> theme = new LinkedHashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @Column(name = "owner_email", length = 150)
    private String ownerEmail;

    @Column(name = "owner_phone", length = 50)
    private String ownerPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreStatus status = StoreStatus.PENDING;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @Column(name = "status_reason", columnDefinition = "text")
    private String statusReason;

    @Column(name = "rating_average", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;
}

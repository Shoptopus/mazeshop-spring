package com.shoptopus.core.payment;

import com.shoptopus.core.common.jpa.AuditSnapshotCarrier;
import com.shoptopus.core.logging.AuditEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "payment_provider_configs")
@EntityListeners(AuditEntityListener.class)
public class PaymentProviderConfig implements AuditSnapshotCarrier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_provider_id", nullable = false)
    private PaymentProvider paymentProvider;

    @Column(nullable = false)
    private String setting;

    @Column
    private String value;

    @Column(name = "test_value")
    private String testValue;

    @Column(name = "public_value", nullable = false)
    private boolean publicValue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Transient
    private Map<String, Object> auditSnapshot;

    @PrePersist
    void onCreate() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

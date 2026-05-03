package com.shoptopus.core.payment;

import com.shoptopus.core.common.jpa.AuditSnapshotCarrier;
import com.shoptopus.core.logging.AuditEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "payment_providers")
@EntityListeners(AuditEntityListener.class)
public class PaymentProvider implements AuditSnapshotCarrier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "test_mode", nullable = false)
    private boolean testMode = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "paymentProvider", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentProviderConfig> configs = new ArrayList<>();

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

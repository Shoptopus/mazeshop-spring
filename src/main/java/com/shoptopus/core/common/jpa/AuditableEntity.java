package com.shoptopus.core.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import com.shoptopus.core.logging.AuditEntityListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.OffsetDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditEntityListener.class)
public abstract class AuditableEntity extends UuidEntity implements AuditSnapshotCarrier {

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Transient
    private Map<String, Object> auditSnapshot = new LinkedHashMap<>();

    @PrePersist
    void prePersistTimestamps() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdateTimestamps() {
        updatedAt = OffsetDateTime.now();
    }
}

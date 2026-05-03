package com.shoptopus.core.logging;

import com.shoptopus.core.common.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "event_logs")
public class EventLog extends AuditableEntity {

    @Column(name = "store_id")
    private UUID storeId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(nullable = false)
    private Integer type;

    @Column(name = "eventable_type")
    private String eventableType;

    @Column(name = "eventable_id")
    private String eventableId;

    @Column(columnDefinition = "text", nullable = false)
    private String data;
}

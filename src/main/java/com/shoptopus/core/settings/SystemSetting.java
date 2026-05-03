package com.shoptopus.core.settings;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "system_settings")
public class SystemSetting extends SoftDeleteEntity {

    @Column(name = "key", nullable = false, length = 60)
    private String key;

    @Column(name = "value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(nullable = false)
    private Integer type = 0;
}

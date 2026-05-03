package com.shoptopus.core.files;

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
@Table(name = "paid_file_contents")
public class PaidFileContent extends AuditableEntity {

    @Column(nullable = false)
    private String url;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "fileable_type", nullable = false)
    private String fileableType;

    @Column(name = "fileable_id", nullable = false)
    private UUID fileableId;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 50)
    private String size;

    @Column(nullable = false)
    private Integer type = 1;
}

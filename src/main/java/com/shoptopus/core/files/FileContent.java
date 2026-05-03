package com.shoptopus.core.files;

import com.shoptopus.core.common.jpa.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "file_contents")
public class FileContent extends SoftDeleteEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "size")
    private Long size;

    @Column(name = "path")
    private String path;

    @Column(name = "type")
    private String type;

    @Column(name = "fileable_type")
    private String fileableType;

    @Column(name = "fileable_id")
    private UUID fileableId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;
}

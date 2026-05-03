package com.shoptopus.core.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record PaidFileContentResponse(
        UUID id,
        String url,
        @JsonProperty("original_file_name") String originalFileName,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("fileable_type") String fileableType,
        @JsonProperty("fileable_id") UUID fileableId,
        String title,
        String description,
        String size,
        Integer type
) {
}

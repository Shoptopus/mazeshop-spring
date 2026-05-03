package com.shoptopus.core.email.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmailArchiveResponse(
        Long id,
        String address,
        String subject,
        @JsonProperty("template_id") String templateId,
        String content
) {
}

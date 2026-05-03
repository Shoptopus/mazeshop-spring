package com.shoptopus.core.admin.dto;

import java.util.Map;

public record AdminMutationResponse(
        String status,
        Map<String, Object> payload
) {
}

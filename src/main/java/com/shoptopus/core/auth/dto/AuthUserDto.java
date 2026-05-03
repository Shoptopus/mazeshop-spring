package com.shoptopus.core.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuthUserDto(
        UUID id,
        String name,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String email,
        String phone,
        Map<String, Object> avatar,
        @JsonProperty("is_verified") boolean isVerified,
        Map<String, Object> cart,
        List<Map<String, Object>> notifications,
        List<UUID> favorites
) {
}

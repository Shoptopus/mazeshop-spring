package com.shoptopus.core.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AccountResponse(
        UUID id,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String email,
        String phone,
        String name
) {
}

package com.shoptopus.core.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthPayloadDto(
        String token,
        @JsonProperty("token_type")
        String tokenType,
        AuthUserDto user
) {
}

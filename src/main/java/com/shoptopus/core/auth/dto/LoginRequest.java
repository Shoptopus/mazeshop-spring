package com.shoptopus.core.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.UUID;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @JsonAlias("cart_id") UUID cartId
) {
}

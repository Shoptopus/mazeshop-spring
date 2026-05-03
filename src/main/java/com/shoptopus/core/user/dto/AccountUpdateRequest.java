package com.shoptopus.core.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AccountUpdateRequest(
        @JsonAlias("first_name") @NotBlank String firstName,
        @JsonAlias("last_name") @NotBlank String lastName,
        @Email String email,
        String phone
) {
}

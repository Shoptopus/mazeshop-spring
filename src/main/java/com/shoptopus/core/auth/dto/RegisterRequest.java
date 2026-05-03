package com.shoptopus.core.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @JsonAlias("first_name") @NotBlank @Size(max = 100) String firstName,
        @JsonAlias("last_name") @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 250) String password,
        @JsonAlias("password_confirmation") String passwordConfirmation
) {
}

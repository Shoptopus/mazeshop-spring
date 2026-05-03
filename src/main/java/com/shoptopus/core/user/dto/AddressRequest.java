package com.shoptopus.core.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddressRequest(
        @JsonAlias("address_line_1") @NotBlank String addressLine1,
        @JsonAlias("address_line_2") String addressLine2,
        String note,
        @NotBlank String town,
        @JsonAlias("post_code") @NotBlank String postCode,
        @NotBlank String country,
        String name,
        @Pattern(
                regexp = "^[-]?(([0-8]?[0-9])\\.(\\d+))|(90(\\.0+)?)$",
                message = "must be a valid latitude"
        )
        String lat,
        @Pattern(
                regexp = "^[-]?((((1[0-7][0-9])|([0-9]?[0-9]))\\.(\\d+))|180(\\.0+)?)$",
                message = "must be a valid longitude"
        )
        String lon
) {
}

package com.shoptopus.core.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        @JsonProperty("address_line_1") String addressLine1,
        @JsonProperty("address_line_2") String addressLine2,
        String note,
        String town,
        @JsonProperty("post_code") String postCode,
        String country,
        String name,
        String lat,
        String lon
) {
}

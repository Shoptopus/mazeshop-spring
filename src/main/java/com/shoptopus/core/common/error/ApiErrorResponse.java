package com.shoptopus.core.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        @JsonProperty("user_message") String userMessage,
        @JsonProperty("developer_message") String developerMessage,
        Map<String, List<String>> errors,
        @JsonProperty("error_code") Integer errorCode
) {
}

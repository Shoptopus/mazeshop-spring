package com.shoptopus.core.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String message,
        T data,
        Integer records,
        @JsonProperty("total_records") Long totalRecords,
        String next
) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(message, data, null, null, null);
    }

    public static <T> ApiResponse<T> paged(String message, T data, int records, long totalRecords, String next) {
        return new ApiResponse<>(message, data, records, totalRecords, next);
    }
}

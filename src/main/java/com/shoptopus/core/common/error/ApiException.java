package com.shoptopus.core.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String userMessage;
    private final Integer errorCode;
    private final Map<String, List<String>> errors;

    public ApiException(HttpStatus status, String userMessage, String developerMessage) {
        this(status, userMessage, developerMessage, null, null);
    }

    public ApiException(HttpStatus status, String userMessage, String developerMessage, Integer errorCode) {
        this(status, userMessage, developerMessage, errorCode, null);
    }

    public ApiException(HttpStatus status, String userMessage, String developerMessage, Map<String, List<String>> errors) {
        this(status, userMessage, developerMessage, null, errors);
    }

    public ApiException(HttpStatus status, String userMessage, String developerMessage, Integer errorCode, Map<String, List<String>> errors) {
        super(developerMessage);
        this.status = status;
        this.userMessage = userMessage;
        this.errorCode = errorCode;
        this.errors = errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }
}

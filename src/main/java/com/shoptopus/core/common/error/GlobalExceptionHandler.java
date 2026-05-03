package com.shoptopus.core.common.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        Integer errorCode = ex.getErrorCode();
        if (errorCode == null && ex.getStatus() == HttpStatus.UNAUTHORIZED) {
            errorCode = 1101;
        }
        return ResponseEntity.status(ex.getStatus())
                .body(errorBody(ex.getUserMessage(), ex.getMessage(), ex.getErrors(), errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), List.of(fieldError.getDefaultMessage()));
        }
        return ResponseEntity.badRequest().body(errorBody("Validation failed", "Request validation failed", errors, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(errorBody("Validation failed", ex.getMessage(), null, null));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.badRequest().body(errorBody("Validation failed", "Request validation failed", null, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(errorBody("Validation failed", ex.getMessage(), null, null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(errorBody("Validation failed", ex.getMessage(), null, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody(
                        "Sorry there was a system error, the administrator has been informed.",
                        "This action is unauthorized.",
                        null,
                        1403
                ));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody("Not found", ex.getMessage(), null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        log.error("Unhandled application exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("Unexpected error", deepestMessage(ex), null, null));
    }

    private Map<String, Object> errorBody(
            String userMessage,
            String developerMessage,
            Map<String, List<String>> errors,
            Integer errorCode
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_message", userMessage);
        body.put("developer_message", developerMessage);
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }
        if (errorCode != null) {
            body.put("error_code", errorCode);
        }
        return body;
    }

    private String deepestMessage(Throwable throwable) {
        if (throwable instanceof TransactionSystemException transactionSystemException) {
            Throwable applicationException = transactionSystemException.getApplicationException();
            if (applicationException != null) {
                String message = deepestMessage(applicationException);
                if (message != null && !message.isBlank()) {
                    return message;
                }
            }
        }
        Throwable mostSpecific = NestedExceptionUtils.getMostSpecificCause(throwable);
        if (mostSpecific != null && mostSpecific != throwable) {
            String specificMessage = mostSpecific.getMessage();
            if (specificMessage != null && !specificMessage.isBlank()) {
                return mostSpecific.getClass().getSimpleName() + ": " + specificMessage;
            }
        }
        Throwable current = throwable;
        String message = current.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }
}

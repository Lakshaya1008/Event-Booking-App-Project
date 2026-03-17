package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Error Response DTO
 *
 * Returned by GlobalExceptionHandler for all error responses.
 *
 * Fields:
 * - error:            Short machine-readable error code (e.g. "VALIDATION_ERROR", "EMAIL_ALREADY_REGISTERED")
 * - message:          Human-readable summary of what went wrong
 * - statusCode:       HTTP status code (400, 401, 403, 404, 409, 422, 500)
 * - statusDescription: HTTP status text + context
 * - timestamp:        When the error occurred (ISO-8601)
 * - path:             The API endpoint that was called
 * - validationErrors: FIX — List of ALL field-level validation errors (populated for 400 validation failures)
 *                     Each entry is in the format "fieldName: errorMessage"
 *                     Allows clients to display all problems at once instead of one at a time
 * - possibleCauses:   Diagnostic hints about what might have caused the error
 * - solutions:        Suggested fixes for the caller
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorDto {
    private String error;
    private String message;
    private int statusCode;
    private String statusDescription;
    private String timestamp;
    private String path;

    /**
     * FIX: List of ALL field-level validation errors.
     *
     * Previously GlobalExceptionHandler only returned the FIRST field error.
     * If a register request had 3 missing/invalid fields, the client had to
     * fix one → resubmit → see next error → fix → resubmit → etc.
     *
     * Now ALL validation errors are returned at once in this list.
     * Format: ["email: Email is required", "password: Password must contain at least one uppercase letter"]
     *
     * Null for non-validation errors (e.g. 409 conflicts, 500 server errors).
     */
    private List<String> validationErrors;

    private List<String> possibleCauses;
    private List<String> solutions;

    // Constructor for simple error messages (backward compatibility)
    public ErrorDto(String error) {
        this.error = error;
        this.timestamp = LocalDateTime.now().toString();
    }
}
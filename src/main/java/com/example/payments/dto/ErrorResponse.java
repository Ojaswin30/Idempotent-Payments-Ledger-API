package com.example.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error response")
public record ErrorResponse(
        @Schema(description = "Timestamp of error occurrence")
        Instant timestamp,

        @Schema(description = "HTTP Status code", example = "400")
        int status,

        @Schema(description = "Error category/code", example = "VALIDATION_ERROR")
        String error,

        @Schema(description = "Detailed human-readable message", example = "Amount must be greater than zero")
        String message,

        @Schema(description = "Request path where error occurred", example = "/api/v1/payments")
        String path,

        @Schema(description = "Distributed trace ID for log correlation", example = "4bf92f3577b34da6a3ce929d0e0e4736")
        String traceId,

        @Schema(description = "Field-level validation error details")
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, traceId, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, String traceId, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, path, traceId, details);
    }
}

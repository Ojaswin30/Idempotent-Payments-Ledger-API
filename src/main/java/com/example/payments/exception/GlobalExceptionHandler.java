package com.example.payments.exception;

import com.example.payments.dto.ErrorResponse;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Tracer tracer;

    private String getTraceId() {
        if (tracer != null && tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            return tracer.currentSpan().context().traceId();
        }
        String mdcTrace = MDC.get("traceId");
        return mdcTrace != null ? mdcTrace : "none";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        log.warn("Validation error on {}: {}", request.getRequestURI(), details);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Input validation failed",
                request.getRequestURI(),
                getTraceId(),
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        log.warn("Constraint violation on {}: {}", request.getRequestURI(), details);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Constraint validation failed",
                request.getRequestURI(),
                getTraceId(),
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        log.warn("Missing required header '{}' on {}", ex.getHeaderName(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "MISSING_HEADER",
                "Required header '" + ex.getHeaderName() + "' is missing",
                request.getRequestURI(),
                getTraceId()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(
            PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("Payment not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "PAYMENT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex, HttpServletRequest request) {
        log.warn("Idempotency conflict for key '{}'", ex.getIdempotencyKey());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "IDEMPOTENCY_CONFLICT",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(errorResponse);
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyPayloadMismatch(
            IdempotencyPayloadMismatchException ex, HttpServletRequest request) {
        log.warn("Idempotency payload mismatch: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "IDEMPOTENCY_PAYLOAD_MISMATCH",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentState(
            InvalidPaymentStateException ex, HttpServletRequest request) {
        log.warn("Invalid payment state: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PAYMENT_STATE",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict on {}", request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONCURRENT_MODIFICATION_CONFLICT",
                "The resource was modified concurrently by another transaction. Please retry.",
                request.getRequestURI(),
                getTraceId()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal error processing request {}", request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred while processing the request",
                request.getRequestURI(),
                getTraceId()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

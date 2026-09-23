package com.example.payments.controller;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.ErrorResponse;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.service.IdempotencyService;
import com.example.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments API", description = "Production-grade payment processing with distributed idempotency and ledgering")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @Operation(
            summary = "Create a new payment",
            description = "Creates a payment, records double-entry ledger lines, and stages an event in the outbox. Guaranteed idempotent via the Idempotency-Key header."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Payment created successfully",
                    headers = @Header(name = "X-Idempotent-Replay", description = "'true' if replayed from cache, 'false' if newly processed", schema = @Schema(type = "string")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or missing header",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Concurrent request in progress for this Idempotency-Key",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Idempotency-Key was previously used with a different request payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(description = "Unique client-generated idempotency key (UUID or alphanumeric)", required = true, example = "8d9f4e2a-1c3b-4a5e-9f8d-123456789abc")
            @RequestHeader("Idempotency-Key") @NotBlank(message = "Idempotency-Key header is required") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        log.info("Received POST /payments with Idempotency-Key='{}', customerId='{}', amount='{}'",
                idempotencyKey, request.customerId(), request.amount());

        // 1. Check idempotency layer
        Optional<PaymentResponse> cachedResponse = idempotencyService.processOrAcquireLock(idempotencyKey, request);
        if (cachedResponse.isPresent()) {
            return ResponseEntity.status(HttpStatus.OK)
                    .header("X-Idempotent-Replay", "true")
                    .body(cachedResponse.get());
        }

        // 2. Process business transaction
        PaymentResponse response;
        try {
            response = paymentService.createPayment(request);
            String hash = idempotencyService.computePayloadHash(request);
            idempotencyService.markCompleted(idempotencyKey, hash, response);
        } catch (Exception e) {
            log.error("Failed to create payment for key '{}'. Releasing idempotency lock.", idempotencyKey, e);
            idempotencyService.releaseLock(idempotencyKey);
            throw e;
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Idempotent-Replay", "false")
                .body(response);
    }

    @Operation(summary = "Get payment details by ID", description = "Retrieves payment state and associated double-entry ledger lines")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/{paymentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "Payment UUID", required = true, example = "7c2d1b82-8491-4e94-81d0-9dfa5dbb7e88")
            @PathVariable UUID paymentId
    ) {
        log.info("Fetching payment details for paymentId={}", paymentId);
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List payments for a customer", description = "Fetches a paginated list of payments for a given customer ID")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<PaymentResponse>> listPayments(
            @Parameter(description = "Customer ID", required = true, example = "cust_123")
            @RequestParam String customerId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Listing payments for customerId='{}' (page={}, size={})", customerId, page, size);
        Page<PaymentResponse> payments = paymentService.listPaymentsByCustomer(customerId, PageRequest.of(page, size));
        return ResponseEntity.ok(payments);
    }

    @Operation(summary = "Cancel a payment", description = "Cancels an existing payment, writes reversal ledger entries, and publishes a cancellation event")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment cancelled", content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payment cannot be cancelled in its current state", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{paymentId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> cancelPayment(
            @Parameter(description = "Payment UUID to cancel", required = true)
            @PathVariable UUID paymentId,
            @Parameter(description = "Reason for cancellation", example = "Customer requested order cancellation")
            @RequestParam(required = false) String reason
    ) {
        log.info("Requesting cancellation for paymentId={}, reason='{}'", paymentId, reason);
        PaymentResponse response = paymentService.cancelPayment(paymentId, reason);
        return ResponseEntity.ok(response);
    }
}

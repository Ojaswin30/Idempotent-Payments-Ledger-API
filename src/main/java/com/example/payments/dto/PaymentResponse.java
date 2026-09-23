package com.example.payments.dto;

import com.example.payments.entity.Payment;
import com.example.payments.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Payment details response")
public record PaymentResponse(
        @Schema(description = "Unique payment identifier", example = "7c2d1b82-8491-4e94-81d0-9dfa5dbb7e88")
        UUID paymentId,

        @Schema(description = "Customer ID", example = "cust_123")
        String customerId,

        @Schema(description = "Payment amount", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "ISO 4217 Currency", example = "INR")
        String currency,

        @Schema(description = "Transaction description", example = "Order #12345")
        String description,

        @Schema(description = "Current payment status", example = "CREATED")
        PaymentStatus status,

        @Schema(description = "Optimistic locking version", example = "0")
        Long version,

        @Schema(description = "Timestamp when payment was created")
        Instant createdAt,

        @Schema(description = "Timestamp when payment was last updated")
        Instant updatedAt,

        @Schema(description = "Associated double-entry ledger records")
        List<LedgerEntryResponse> ledgerEntries
) {
    public static PaymentResponse fromEntity(Payment payment) {
        List<LedgerEntryResponse> ledgerList = payment.getLedgerEntries() == null ? List.of() :
                payment.getLedgerEntries().stream()
                        .map(LedgerEntryResponse::fromEntity)
                        .toList();

        return new PaymentResponse(
                payment.getId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getStatus(),
                payment.getVersion(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                ledgerList
        );
    }
}

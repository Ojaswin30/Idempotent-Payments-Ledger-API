package com.example.payments.dto;

import com.example.payments.entity.LedgerEntry;
import com.example.payments.entity.LedgerEntryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Double-entry ledger entry record")
public record LedgerEntryResponse(
        @Schema(description = "Ledger entry UUID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID id,

        @Schema(description = "Associated Account ID", example = "cust_123")
        String accountId,

        @Schema(description = "Entry type (DEBIT or CREDIT)", example = "DEBIT")
        LedgerEntryType entryType,

        @Schema(description = "Amount", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Currency", example = "INR")
        String currency,

        @Schema(description = "Timestamp when entry was recorded")
        Instant createdAt
) {
    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccountId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getCreatedAt()
        );
    }
}

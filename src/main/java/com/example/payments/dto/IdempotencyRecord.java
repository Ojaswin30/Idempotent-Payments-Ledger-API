package com.example.payments.dto;

import java.io.Serializable;
import java.time.Instant;

public record IdempotencyRecord(
        String status, // IN_PROGRESS, COMPLETED
        String requestHash,
        PaymentResponse response,
        Instant createdAt
) implements Serializable {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equalsIgnoreCase(status);
    }
}

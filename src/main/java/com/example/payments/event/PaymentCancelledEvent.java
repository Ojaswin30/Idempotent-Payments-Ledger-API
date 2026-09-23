package com.example.payments.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCancelledEvent(
        UUID eventId,
        String eventType,
        UUID paymentId,
        String customerId,
        BigDecimal amount,
        String currency,
        String reason,
        Instant occurredAt
) {
    public static final String EVENT_TYPE = "PAYMENT_CANCELLED";

    public static PaymentCancelledEvent of(UUID paymentId, String customerId, BigDecimal amount, String currency, String reason) {
        return new PaymentCancelledEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                paymentId,
                customerId,
                amount,
                currency,
                reason,
                Instant.now()
        );
    }
}

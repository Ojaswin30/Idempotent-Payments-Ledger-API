package com.example.payments.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCreatedEvent(
        UUID eventId,
        String eventType,
        UUID paymentId,
        String customerId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
    public static final String EVENT_TYPE = "PAYMENT_CREATED";

    public static PaymentCreatedEvent of(UUID paymentId, String customerId, BigDecimal amount, String currency) {
        return new PaymentCreatedEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                paymentId,
                customerId,
                amount,
                currency,
                Instant.now()
        );
    }
}

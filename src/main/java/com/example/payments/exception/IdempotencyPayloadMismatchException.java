package com.example.payments.exception;

public class IdempotencyPayloadMismatchException extends RuntimeException {
    public IdempotencyPayloadMismatchException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' has already been used with a different request payload.");
    }
}

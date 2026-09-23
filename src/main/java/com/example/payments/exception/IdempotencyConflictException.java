package com.example.payments.exception;

public class IdempotencyConflictException extends RuntimeException {
    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super("A concurrent request with Idempotency-Key '" + idempotencyKey + "' is currently in progress. Please retry shortly.");
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

package com.example.payments.service;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.IdempotencyRecord;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.exception.IdempotencyConflictException;
import com.example.payments.exception.IdempotencyPayloadMismatchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.idempotency.in-progress-ttl-seconds:30}")
    private long inProgressTtlSeconds;

    @Value("${app.idempotency.completed-ttl-seconds:86400}")
    private long completedTtlSeconds;

    private static final String REDIS_KEY_PREFIX = "idempotency:payments:";

    public String computePayloadHash(CreatePaymentRequest request) {
        try {
            String raw = String.format("%s|%s|%s|%s",
                    request.customerId() != null ? request.customerId().trim() : "",
                    request.amount() != null ? request.amount().stripTrailingZeros().toPlainString() : "",
                    request.currency() != null ? request.currency().trim().toUpperCase() : "",
                    request.description() != null ? request.description().trim() : ""
            );
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Attempts to acquire an idempotency lock for the given key and payload.
     *
     * @param idempotencyKey Key provided by client in header
     * @param request        Payment creation payload
     * @return Optional containing cached PaymentResponse if already completed, empty if lock acquired
     */
    public Optional<PaymentResponse> processOrAcquireLock(String idempotencyKey, CreatePaymentRequest request) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;
        String currentHash = computePayloadHash(request);

        IdempotencyRecord initialRecord = new IdempotencyRecord(
                IdempotencyRecord.STATUS_IN_PROGRESS,
                currentHash,
                null,
                Instant.now()
        );

        // Atomic SET key value NX EX inProgressTtlSeconds
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, initialRecord, Duration.ofSeconds(inProgressTtlSeconds));

        if (Boolean.TRUE.equals(lockAcquired)) {
            log.info("Acquired idempotency lock for key='{}' with TTL={}s", idempotencyKey, inProgressTtlSeconds);
            return Optional.empty();
        }

        // Lock was not acquired: key already exists
        Object existingValue = redisTemplate.opsForValue().get(redisKey);
        if (existingValue == null) {
            // Edge case: expired right between SETNX failure and GET, retry acquisition
            Boolean retryLock = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, initialRecord, Duration.ofSeconds(inProgressTtlSeconds));
            if (Boolean.TRUE.equals(retryLock)) {
                return Optional.empty();
            }
            throw new IdempotencyConflictException(idempotencyKey);
        }

        IdempotencyRecord record = convertToRecord(existingValue);

        // 1. Validate payload fingerprint match
        if (record.requestHash() != null && !record.requestHash().equals(currentHash)) {
            log.warn("Payload mismatch for idempotency key='{}'. Expected hash={}, got hash={}",
                    idempotencyKey, record.requestHash(), currentHash);
            throw new IdempotencyPayloadMismatchException(idempotencyKey);
        }

        // 2. Handle IN_PROGRESS state
        if (record.isInProgress()) {
            log.warn("Concurrent request collision for key='{}' (state: IN_PROGRESS)", idempotencyKey);
            throw new IdempotencyConflictException(idempotencyKey);
        }

        // 3. Handle COMPLETED state
        if (record.isCompleted() && record.response() != null) {
            log.info("Returning cached idempotent response for key='{}', paymentId={}",
                    idempotencyKey, record.response().paymentId());
            return Optional.of(record.response());
        }

        throw new IdempotencyConflictException(idempotencyKey);
    }

    /**
     * Stores the final successful response with a 24-hour TTL.
     */
    public void markCompleted(String idempotencyKey, String requestHash, PaymentResponse response) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;
        IdempotencyRecord completedRecord = new IdempotencyRecord(
                IdempotencyRecord.STATUS_COMPLETED,
                requestHash,
                response,
                Instant.now()
        );

        redisTemplate.opsForValue()
                .set(redisKey, completedRecord, Duration.ofSeconds(completedTtlSeconds));
        log.info("Marked idempotency key='{}' as COMPLETED with TTL={}s", idempotencyKey, completedTtlSeconds);
    }

    /**
     * Releases the lock in the event of an unrecoverable pre-commit application failure.
     */
    public void releaseLock(String idempotencyKey) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;
        redisTemplate.delete(redisKey);
        log.debug("Released idempotency lock for key='{}'", idempotencyKey);
    }

    private IdempotencyRecord convertToRecord(Object value) {
        if (value instanceof IdempotencyRecord record) {
            return record;
        }
        return objectMapper.convertValue(value, IdempotencyRecord.class);
    }
}

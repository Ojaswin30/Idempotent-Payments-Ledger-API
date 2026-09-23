package com.example.payments.service;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.IdempotencyRecord;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.entity.PaymentStatus;
import com.example.payments.exception.IdempotencyConflictException;
import com.example.payments.exception.IdempotencyPayloadMismatchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(idempotencyService, "inProgressTtlSeconds", 30L);
        ReflectionTestUtils.setField(idempotencyService, "completedTtlSeconds", 86400L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should acquire lock successfully on first request")
    void shouldAcquireLockSuccessfully() {
        String key = "test-key-1";
        CreatePaymentRequest request = new CreatePaymentRequest("cust_1", new BigDecimal("100.00"), "USD", "Test");

        when(valueOperations.setIfAbsent(eq("idempotency:payments:" + key), any(IdempotencyRecord.class), eq(Duration.ofSeconds(30))))
                .thenReturn(true);

        Optional<PaymentResponse> result = idempotencyService.processOrAcquireLock(key, request);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return cached response when request is already completed")
    void shouldReturnCachedResponseWhenCompleted() {
        String key = "test-key-completed";
        CreatePaymentRequest request = new CreatePaymentRequest("cust_1", new BigDecimal("100.00"), "USD", "Test");
        String hash = idempotencyService.computePayloadHash(request);

        PaymentResponse cachedResponse = new PaymentResponse(
                UUID.randomUUID(), "cust_1", new BigDecimal("100.00"), "USD", "Test",
                PaymentStatus.CREATED, 0L, Instant.now(), Instant.now(), List.of()
        );

        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyRecord.STATUS_COMPLETED,
                hash,
                cachedResponse,
                Instant.now()
        );

        when(valueOperations.setIfAbsent(eq("idempotency:payments:" + key), any(IdempotencyRecord.class), eq(Duration.ofSeconds(30))))
                .thenReturn(false);
        when(valueOperations.get("idempotency:payments:" + key)).thenReturn(record);

        Optional<PaymentResponse> result = idempotencyService.processOrAcquireLock(key, request);

        assertThat(result).isPresent();
        assertThat(result.get().paymentId()).isEqualTo(cachedResponse.paymentId());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when request is in progress")
    void shouldThrowConflictWhenInProgress() {
        String key = "test-key-in-progress";
        CreatePaymentRequest request = new CreatePaymentRequest("cust_1", new BigDecimal("100.00"), "USD", "Test");
        String hash = idempotencyService.computePayloadHash(request);

        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyRecord.STATUS_IN_PROGRESS,
                hash,
                null,
                Instant.now()
        );

        when(valueOperations.setIfAbsent(eq("idempotency:payments:" + key), any(IdempotencyRecord.class), eq(Duration.ofSeconds(30))))
                .thenReturn(false);
        when(valueOperations.get("idempotency:payments:" + key)).thenReturn(record);

        assertThatThrownBy(() -> idempotencyService.processOrAcquireLock(key, request))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Should throw IdempotencyPayloadMismatchException when payload hash differs")
    void shouldThrowPayloadMismatchWhenHashDiffers() {
        String key = "test-key-mismatch";
        CreatePaymentRequest originalRequest = new CreatePaymentRequest("cust_1", new BigDecimal("100.00"), "USD", "Original");
        CreatePaymentRequest modifiedRequest = new CreatePaymentRequest("cust_1", new BigDecimal("500.00"), "USD", "Modified");

        String originalHash = idempotencyService.computePayloadHash(originalRequest);

        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyRecord.STATUS_COMPLETED,
                originalHash,
                null,
                Instant.now()
        );

        when(valueOperations.setIfAbsent(eq("idempotency:payments:" + key), any(IdempotencyRecord.class), eq(Duration.ofSeconds(30))))
                .thenReturn(false);
        when(valueOperations.get("idempotency:payments:" + key)).thenReturn(record);

        assertThatThrownBy(() -> idempotencyService.processOrAcquireLock(key, modifiedRequest))
                .isInstanceOf(IdempotencyPayloadMismatchException.class);
    }

    @Test
    @DisplayName("Should save completed record with 24-hour TTL")
    void shouldSaveCompletedRecord() {
        String key = "test-key-complete";
        String hash = "dummy-hash";
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(), "cust_1", new BigDecimal("100.00"), "USD", "Test",
                PaymentStatus.CREATED, 0L, Instant.now(), Instant.now(), List.of()
        );

        idempotencyService.markCompleted(key, hash, response);

        verify(valueOperations).set(
                eq("idempotency:payments:" + key),
                any(IdempotencyRecord.class),
                eq(Duration.ofSeconds(86400))
        );
    }
}

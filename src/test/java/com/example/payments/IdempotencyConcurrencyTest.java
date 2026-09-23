package com.example.payments;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.entity.Payment;
import com.example.payments.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Concurrent requests with identical Idempotency-Key must produce exactly ONE payment in DB")
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        int numberOfThreads = 50;
        String idempotencyKey = "concurrent-key-" + UUID.randomUUID();

        CreatePaymentRequest request = new CreatePaymentRequest(
                "cust_concurrent_999",
                new BigDecimal("99.99"),
                "USD",
                "Concurrent Stress Test"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger cachedReplayCount = new AtomicInteger(0);
        Set<UUID> createdPaymentIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // release all threads at once

                    ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                            "/api/v1/payments",
                            HttpMethod.POST,
                            entity,
                            PaymentResponse.class
                    );

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        createdCount.incrementAndGet();
                        if (response.getBody() != null) {
                            createdPaymentIds.add(response.getBody().paymentId());
                        }
                    } else if (response.getStatusCode() == HttpStatus.OK) {
                        cachedReplayCount.incrementAndGet();
                        if (response.getBody() != null) {
                            createdPaymentIds.add(response.getBody().paymentId());
                        }
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Log error if any
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(createdCount.get()).isEqualTo(1);
        assertThat(createdPaymentIds).hasSize(1);
        assertThat(createdCount.get() + conflictCount.get() + cachedReplayCount.get()).isEqualTo(numberOfThreads);

        List<Payment> dbPayments = paymentRepository.findAll().stream()
                .filter(p -> "cust_concurrent_999".equals(p.getCustomerId()))
                .toList();

        assertThat(dbPayments).hasSize(1);
        assertThat(dbPayments.get(0).getId()).isEqualTo(createdPaymentIds.iterator().next());
    }
}

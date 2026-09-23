package com.example.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency stress test that executes 50 parallel requests directly against
 * the live running Docker Compose API server at http://localhost:8080.
 */
public class IdempotencyConcurrencyLiveTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    @DisplayName("Live Stress Test: 50 concurrent requests with identical Idempotency-Key against live Docker API")
    void shouldHandleLiveConcurrentRequests() throws Exception {
        int numberOfThreads = 50;
        String idempotencyKey = "concurrent-live-" + UUID.randomUUID();
        String jsonPayload = """
                {
                  "customerId": "cust_live_stress_999",
                  "amount": 1999.00,
                  "currency": "INR",
                  "description": "Live Concurrency Stress Test"
                }
                """;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger replayCount = new AtomicInteger(0);
        Set<String> paymentIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Release all threads at the exact same millisecond

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/v1/payments"))
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", idempotencyKey)
                            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                            .timeout(Duration.ofSeconds(10))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();

                    if (statusCode == 201) {
                        createdCount.incrementAndGet();
                        JsonNode node = objectMapper.readTree(response.body());
                        paymentIds.add(node.get("paymentId").asText());
                    } else if (statusCode == 200) {
                        replayCount.incrementAndGet();
                        JsonNode node = objectMapper.readTree(response.body());
                        paymentIds.add(node.get("paymentId").asText());
                    } else if (statusCode == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignored in benchmark
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown(); // FIRE ALL 50 THREADS CONCURRENTLY
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        System.out.println("=================================================");
        System.out.println(" CONCURRENCY STRESS TEST RESULTS (50 THREADS)");
        System.out.println(" 201 Created Responses : " + createdCount.get());
        System.out.println(" 200 Cached Replays    : " + replayCount.get());
        System.out.println(" 409 Conflicts         : " + conflictCount.get());
        System.out.println(" Distinct Payment IDs  : " + paymentIds.size());
        System.out.println("=================================================");

        // Exactly 1 thread must have created the payment in PostgreSQL
        assertThat(createdCount.get()).isEqualTo(1);

        // All successful responses must reference the exact same payment ID
        assertThat(paymentIds).hasSize(1);

        // All 50 threads handled cleanly
        assertThat(createdCount.get() + replayCount.get() + conflictCount.get()).isEqualTo(numberOfThreads);
    }
}

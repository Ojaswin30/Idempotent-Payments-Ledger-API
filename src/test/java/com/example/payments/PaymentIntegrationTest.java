package com.example.payments;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.ErrorResponse;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.entity.LedgerEntry;
import com.example.payments.entity.LedgerEntryType;
import com.example.payments.entity.PaymentStatus;
import com.example.payments.repository.LedgerEntryRepository;
import com.example.payments.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    @DisplayName("Test 1: Payment Creation - Should return 201 Created with balanced double-entry ledger records")
    void shouldCreatePaymentWithBalancedLedger() {
        String key = UUID.randomUUID().toString();
        CreatePaymentRequest request = new CreatePaymentRequest(
                "cust_test_100",
                new BigDecimal("1500.00"),
                "INR",
                "Integration Test Order"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                entity,
                PaymentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst("X-Idempotent-Replay")).isEqualTo("false");

        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.paymentId()).isNotNull();
        assertThat(body.customerId()).isEqualTo("cust_test_100");
        assertThat(body.amount()).isEqualByComparingTo("1500.00");
        assertThat(body.currency()).isEqualTo("INR");
        assertThat(body.status()).isEqualTo(PaymentStatus.CREATED);

        // Verify balanced double-entry ledger entries in database
        List<LedgerEntry> entries = ledgerEntryRepository.findByPaymentIdOrderByCreatedAtAsc(body.paymentId());
        assertThat(entries).hasSize(2);

        LedgerEntry customerDebit = entries.stream()
                .filter(e -> e.getAccountId().equals("cust_test_100"))
                .findFirst()
                .orElseThrow();
        assertThat(customerDebit.getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(customerDebit.getAmount()).isEqualByComparingTo("1500.00");

        LedgerEntry escrowCredit = entries.stream()
                .filter(e -> e.getAccountId().equals("SYSTEM_ESCROW_ACCOUNT"))
                .findFirst()
                .orElseThrow();
        assertThat(escrowCredit.getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(escrowCredit.getAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Test 2: Idempotent Replay - Repeated request with same key returns identical payment ID")
    void shouldReplayIdenticalPaymentForDuplicateKey() {
        String key = UUID.randomUUID().toString();
        CreatePaymentRequest request = new CreatePaymentRequest(
                "cust_test_200",
                new BigDecimal("250.00"),
                "USD",
                "Idempotency Test Order"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);

        // First Request
        ResponseEntity<PaymentResponse> response1 = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                entity,
                PaymentResponse.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response1.getHeaders().getFirst("X-Idempotent-Replay")).isEqualTo("false");

        // Second Request (Duplicate Key)
        ResponseEntity<PaymentResponse> response2 = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                entity,
                PaymentResponse.class
        );
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getHeaders().getFirst("X-Idempotent-Replay")).isEqualTo("true");

        assertThat(response2.getBody()).isNotNull();
        assertThat(response2.getBody().paymentId()).isEqualTo(response1.getBody().paymentId());
    }

    @Test
    @DisplayName("Test 3: Different Keys - Should create two distinct payments")
    void shouldCreateSeparatePaymentsForDifferentKeys() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "cust_test_300",
                new BigDecimal("75.00"),
                "EUR",
                "Order #1"
        );

        HttpHeaders headers1 = new HttpHeaders();
        headers1.setContentType(MediaType.APPLICATION_JSON);
        headers1.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<PaymentResponse> response1 = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(request, headers1),
                PaymentResponse.class
        );

        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.APPLICATION_JSON);
        headers2.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<PaymentResponse> response2 = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(request, headers2),
                PaymentResponse.class
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response1.getBody().paymentId()).isNotEqualTo(response2.getBody().paymentId());
    }

    @Test
    @DisplayName("Test 4: Payload Mismatch - Reusing key with altered payload returns 422 Unprocessable Entity")
    void shouldRejectWhenKeyReusedWithDifferentPayload() {
        String key = UUID.randomUUID().toString();
        CreatePaymentRequest initialRequest = new CreatePaymentRequest(
                "cust_test_400",
                new BigDecimal("100.00"),
                "USD",
                "Initial Payload"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        ResponseEntity<PaymentResponse> response1 = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(initialRequest, headers),
                PaymentResponse.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Altered payload with same key
        CreatePaymentRequest alteredRequest = new CreatePaymentRequest(
                "cust_test_400",
                new BigDecimal("999.00"), // altered amount
                "USD",
                "Initial Payload"
        );

        ResponseEntity<ErrorResponse> response2 = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(alteredRequest, headers),
                ErrorResponse.class
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response2.getBody()).isNotNull();
        assertThat(response2.getBody().error()).isEqualTo("IDEMPOTENCY_PAYLOAD_MISMATCH");
    }

    @Test
    @DisplayName("Test 5: Payment Cancellation - Cancels payment and creates balanced reversal ledger entries")
    void shouldCancelPaymentWithReversalLedger() {
        String key = UUID.randomUUID().toString();
        CreatePaymentRequest request = new CreatePaymentRequest(
                "cust_test_500",
                new BigDecimal("500.00"),
                "INR",
                "Cancel Test Order"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);

        ResponseEntity<PaymentResponse> createResp = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                PaymentResponse.class
        );

        UUID paymentId = createResp.getBody().paymentId();

        // Cancel payment
        ResponseEntity<PaymentResponse> cancelResp = restTemplate.exchange(
                "/api/v1/payments/" + paymentId + "/cancel?reason=UserChangedMind",
                HttpMethod.POST,
                null,
                PaymentResponse.class
        );

        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResp.getBody().status()).isEqualTo(PaymentStatus.CANCELLED);

        // Total 4 ledger entries (2 initial + 2 reversal)
        List<LedgerEntry> entries = ledgerEntryRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
        assertThat(entries).hasSize(4);
    }
}

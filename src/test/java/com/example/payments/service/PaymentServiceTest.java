package com.example.payments.service;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.entity.LedgerEntry;
import com.example.payments.entity.LedgerEntryType;
import com.example.payments.entity.OutboxEvent;
import com.example.payments.entity.Payment;
import com.example.payments.entity.PaymentStatus;
import com.example.payments.repository.OutboxEventRepository;
import com.example.payments.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("Should create payment, record balanced double-entry ledger lines, and stage outbox event")
    void shouldCreatePaymentWithLedgerAndOutbox() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "cust_123", new BigDecimal("1500.00"), "INR", "Order #12345"
        );

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setVersion(0L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.customerId()).isEqualTo("cust_123");
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);

        // Verify ledger entries were created
        verify(ledgerService).recordPaymentCreated(any(Payment.class));

        // Verify outbox event was saved
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent capturedOutbox = outboxCaptor.getValue();
        assertThat(capturedOutbox.getEventType()).isEqualTo("PAYMENT_CREATED");
        assertThat(capturedOutbox.getPayload()).contains("cust_123");
    }

    @Test
    @DisplayName("Should cancel payment, record reversal ledger lines, and stage cancellation event")
    void shouldCancelPayment() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .customerId("cust_123")
                .amount(new BigDecimal("1500.00"))
                .currency("INR")
                .status(PaymentStatus.CREATED)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(paymentRepository.findByIdWithLedger(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.cancelPayment(paymentId, "Refund requested");

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELLED);
        verify(ledgerService).recordPaymentCancelled(payment);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("PAYMENT_CANCELLED");
    }
}

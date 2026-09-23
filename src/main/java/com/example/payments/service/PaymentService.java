package com.example.payments.service;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.entity.OutboxEvent;
import com.example.payments.entity.OutboxStatus;
import com.example.payments.entity.Payment;
import com.example.payments.entity.PaymentStatus;
import com.example.payments.event.PaymentCancelledEvent;
import com.example.payments.event.PaymentCreatedEvent;
import com.example.payments.exception.InvalidPaymentStateException;
import com.example.payments.exception.PaymentNotFoundException;
import com.example.payments.repository.OutboxEventRepository;
import com.example.payments.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Atomically creates a Payment, records double-entry ledger lines, and enqueues an Outbox event.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();

        Payment payment = Payment.builder()
                .id(paymentId)
                .customerId(request.customerId().trim())
                .amount(request.amount())
                .currency(request.currency().trim().toUpperCase())
                .description(request.description() != null ? request.description().trim() : null)
                .status(PaymentStatus.CREATED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 1. Record balanced ledger entries
        ledgerService.recordPaymentCreated(payment);

        // 2. Persist Payment and cascade ledger entries
        Payment savedPayment = paymentRepository.save(payment);

        // 3. Atomically persist Outbox event for Kafka publication
        PaymentCreatedEvent event = PaymentCreatedEvent.of(
                savedPayment.getId(),
                savedPayment.getCustomerId(),
                savedPayment.getAmount(),
                savedPayment.getCurrency()
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(savedPayment.getId())
                    .eventType(PaymentCreatedEvent.EVENT_TYPE)
                    .payload(payloadJson)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(now)
                    .build();

            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event for paymentId={}", savedPayment.getId(), e);
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }

        log.info("Payment created successfully with paymentId={}, amount={} {}",
                savedPayment.getId(), savedPayment.getAmount(), savedPayment.getCurrency());

        return PaymentResponse.fromEntity(savedPayment);
    }

    /**
     * Retrieves a payment by ID along with its ledger entries.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findByIdWithLedger(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Lists payments for a specific customer with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPaymentsByCustomer(String customerId, Pageable pageable) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(PaymentResponse::fromEntity);
    }

    /**
     * Cancels a payment, executing optimistic locking, recording reversal ledger entries, and outbox event.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse cancelPayment(UUID paymentId, String reason) {
        Payment payment = paymentRepository.findByIdWithLedger(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            log.info("Payment {} is already CANCELLED. Returning existing state.", paymentId);
            return PaymentResponse.fromEntity(payment);
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new InvalidPaymentStateException("Cannot cancel a failed payment: " + paymentId);
        }

        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setUpdatedAt(now);

        // Record reversal ledger entries
        ledgerService.recordPaymentCancelled(payment);
        Payment updatedPayment = paymentRepository.save(payment);

        // Record outbox event for cancellation
        PaymentCancelledEvent event = PaymentCancelledEvent.of(
                updatedPayment.getId(),
                updatedPayment.getCustomerId(),
                updatedPayment.getAmount(),
                updatedPayment.getCurrency(),
                reason != null ? reason : "Customer requested cancellation"
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(updatedPayment.getId())
                    .eventType(PaymentCancelledEvent.EVENT_TYPE)
                    .payload(payloadJson)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(now)
                    .build();

            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cancellation outbox event for paymentId={}", paymentId, e);
            throw new IllegalStateException("Failed to serialize cancellation outbox event", e);
        }

        log.info("Payment {} successfully cancelled with version {}", paymentId, updatedPayment.getVersion());
        return PaymentResponse.fromEntity(updatedPayment);
    }
}

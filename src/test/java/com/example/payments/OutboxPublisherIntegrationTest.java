package com.example.payments;

import com.example.payments.dto.CreatePaymentRequest;
import com.example.payments.dto.PaymentResponse;
import com.example.payments.entity.OutboxEvent;
import com.example.payments.entity.OutboxStatus;
import com.example.payments.kafka.PaymentEventConsumer;
import com.example.payments.repository.OutboxEventRepository;
import com.example.payments.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublisherIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentEventConsumer paymentEventConsumer;

    @Test
    @DisplayName("Transactional Outbox: Should publish pending outbox events to Kafka and mark as PUBLISHED")
    void shouldPublishOutboxEventsToKafka() throws InterruptedException {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "cust_outbox_test",
                new BigDecimal("850.00"),
                "INR",
                "Outbox Test Order"
        );

        PaymentResponse response = paymentService.createPayment(request);

        // Wait up to 10 seconds for the Outbox worker to poll and publish to Kafka
        boolean eventReceived = false;
        long deadline = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < deadline) {
            boolean matched = paymentEventConsumer.getReceivedRecords().stream()
                    .anyMatch(record -> record.key().equals(response.paymentId().toString()));
            if (matched) {
                eventReceived = true;
                break;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }

        assertThat(eventReceived).isTrue();

        // Verify outbox table status
        List<OutboxEvent> outboxList = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(response.paymentId()))
                .toList();

        assertThat(outboxList).isNotEmpty();
        OutboxEvent outboxEvent = outboxList.get(0);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
    }
}

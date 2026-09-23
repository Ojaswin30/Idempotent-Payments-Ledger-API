package com.example.payments.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.payment-events:payments.events}")
    private String paymentEventsTopic;

    @Value("${app.kafka.topics.payment-events-dlq:payments.events.dlq}")
    private String paymentEventsDlqTopic;

    public void publishEvent(UUID aggregateId, String eventType, String payloadJson) throws Exception {
        ProducerRecord<String, Object> record = new ProducerRecord<>(paymentEventsTopic, aggregateId.toString(), payloadJson);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add("aggregateId", aggregateId.toString().getBytes(StandardCharsets.UTF_8));

        log.debug("Publishing event to Kafka topic={}, key={}, eventType={}", paymentEventsTopic, aggregateId, eventType);
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);
    }

    public void publishToDlq(UUID aggregateId, String eventType, String payloadJson, String errorMessage) {
        try {
            ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(paymentEventsDlqTopic, aggregateId.toString(), payloadJson);
            dlqRecord.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("aggregateId", aggregateId.toString().getBytes(StandardCharsets.UTF_8));
            if (errorMessage != null) {
                dlqRecord.headers().add("errorReason", errorMessage.getBytes(StandardCharsets.UTF_8));
            }

            log.warn("Routing unrecoverable event to DLQ topic={}, aggregateId={}, eventType={}",
                    paymentEventsDlqTopic, aggregateId, eventType);
            kafkaTemplate.send(dlqRecord).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to route message to DLQ for aggregateId={}", aggregateId, e);
        }
    }
}

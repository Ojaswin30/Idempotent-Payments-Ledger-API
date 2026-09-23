package com.example.payments.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Slf4j
@Component
public class PaymentEventConsumer {

    @Getter
    private final List<ConsumerRecord<String, String>> receivedRecords = new CopyOnWriteArrayList<>();

    @KafkaListener(
            topics = "${app.kafka.topics.payment-events:payments.events}",
            groupId = "${spring.kafka.consumer.group-id:payments-group}"
    )
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        log.info("Consumer received event from topic={}, partition={}, offset={}, key={}, payload={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());

        receivedRecords.add(record);
    }
}

package com.example.payments.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.payment-events:payments.events}")
    private String paymentEventsTopic;

    @Value("${app.kafka.topics.payment-events-dlq:payments.events.dlq}")
    private String paymentEventsDlqTopic;

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(paymentEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsDlqTopic() {
        return TopicBuilder.name(paymentEventsDlqTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

package com.example.payments.outbox;

import com.example.payments.entity.OutboxEvent;
import com.example.payments.entity.OutboxStatus;
import com.example.payments.kafka.PaymentEventPublisher;
import com.example.payments.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherJob {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries;

    @Value("${app.outbox.base-backoff-ms:1000}")
    private long baseBackoffMs;

    @Scheduled(fixedDelayString = "${app.outbox.polling-rate-ms:1000}")
    @Transactional(propagation = Propagation.REQUIRED)
    public void processOutboxEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsForPublishing(now, batchSize);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events for publishing", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                paymentEventPublisher.publishEvent(event.getAggregateId(), event.getEventType(), event.getPayload());

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);

                log.info("Successfully published outbox event id={}, aggregateId={}, eventType={}",
                        event.getId(), event.getAggregateId(), event.getEventType());
            } catch (Exception e) {
                int newRetryCount = event.getRetryCount() + 1;
                event.setRetryCount(newRetryCount);
                event.setLastError(e.getMessage());

                if (newRetryCount >= maxRetries) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event id={} exceeded max retries ({}). Routing to DLQ. Error: {}",
                            event.getId(), maxRetries, e.getMessage());

                    paymentEventPublisher.publishToDlq(
                            event.getAggregateId(),
                            event.getEventType(),
                            event.getPayload(),
                            e.getMessage()
                    );
                } else {
                    // Exponential backoff: baseBackoffMs * 2^(retryCount - 1)
                    long backoffMultiplier = 1L << (newRetryCount - 1);
                    long delayMs = baseBackoffMs * backoffMultiplier;
                    event.setNextRetryAt(Instant.now().plusMillis(delayMs));

                    log.warn("Failed to publish outbox event id={} (attempt {}/{}). Retrying in {}ms. Error: {}",
                            event.getId(), newRetryCount, maxRetries, delayMs, e.getMessage());
                }
            }

            outboxEventRepository.save(event);
        }
    }
}

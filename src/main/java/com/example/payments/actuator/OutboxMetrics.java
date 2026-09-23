package com.example.payments.actuator;

import com.example.payments.entity.OutboxStatus;
import com.example.payments.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("payments.outbox.pending.count", this, OutboxMetrics::getPendingCount)
                .description("Number of pending events in the transactional outbox table")
                .register(meterRegistry);

        Gauge.builder("payments.outbox.failed.count", this, OutboxMetrics::getFailedCount)
                .description("Number of failed events in the transactional outbox table")
                .register(meterRegistry);
    }

    public double getPendingCount() {
        return outboxEventRepository.countByStatus(OutboxStatus.PENDING);
    }

    public double getFailedCount() {
        return outboxEventRepository.countByStatus(OutboxStatus.FAILED);
    }
}

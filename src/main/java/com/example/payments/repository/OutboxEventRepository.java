package com.example.payments.repository;

import com.example.payments.entity.OutboxEvent;
import com.example.payments.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPendingEventsForPublishing(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    long countByStatus(OutboxStatus status);
}

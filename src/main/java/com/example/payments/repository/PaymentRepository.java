package com.example.payments.repository;

import com.example.payments.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Page<Payment> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.ledgerEntries WHERE p.id = :id")
    Optional<Payment> findByIdWithLedger(@Param("id") UUID id);
}

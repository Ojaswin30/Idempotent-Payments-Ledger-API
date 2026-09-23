package com.example.payments.repository;

import com.example.payments.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(String accountId);
}

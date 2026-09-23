package com.example.payments.service;

import com.example.payments.entity.LedgerEntry;
import com.example.payments.entity.LedgerEntryType;
import com.example.payments.entity.Payment;
import com.example.payments.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    public static final String SYSTEM_ESCROW_ACCOUNT = "SYSTEM_ESCROW_ACCOUNT";

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Records balanced double-entry ledger entries for a newly created payment:
     * 1. DEBIT customer account
     * 2. CREDIT system escrow account
     */
    public List<LedgerEntry> recordPaymentCreated(Payment payment) {
        Instant now = Instant.now();

        LedgerEntry customerDebit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .accountId(payment.getCustomerId())
                .entryType(LedgerEntryType.DEBIT)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .createdAt(now)
                .build();

        LedgerEntry escrowCredit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .accountId(SYSTEM_ESCROW_ACCOUNT)
                .entryType(LedgerEntryType.CREDIT)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .createdAt(now)
                .build();

        payment.addLedgerEntry(customerDebit);
        payment.addLedgerEntry(escrowCredit);

        log.debug("Created balanced double-entry ledger records for payment={}: DEBIT {} {}, CREDIT {} {}",
                payment.getId(), customerDebit.getAccountId(), customerDebit.getAmount(),
                escrowCredit.getAccountId(), escrowCredit.getAmount());

        return List.of(customerDebit, escrowCredit);
    }

    /**
     * Records reverse balanced double-entry ledger entries for a cancelled payment:
     * 1. CREDIT customer account (refund)
     * 2. DEBIT system escrow account
     */
    public List<LedgerEntry> recordPaymentCancelled(Payment payment) {
        Instant now = Instant.now();

        LedgerEntry customerCredit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .accountId(payment.getCustomerId())
                .entryType(LedgerEntryType.CREDIT)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .createdAt(now)
                .build();

        LedgerEntry escrowDebit = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .accountId(SYSTEM_ESCROW_ACCOUNT)
                .entryType(LedgerEntryType.DEBIT)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .createdAt(now)
                .build();

        payment.addLedgerEntry(customerCredit);
        payment.addLedgerEntry(escrowDebit);

        log.debug("Created balanced reversal ledger records for payment={}: CREDIT {} {}, DEBIT {} {}",
                payment.getId(), customerCredit.getAccountId(), customerCredit.getAmount(),
                escrowDebit.getAccountId(), escrowDebit.getAmount());

        return List.of(customerCredit, escrowDebit);
    }

    public List<LedgerEntry> getEntriesForPayment(UUID paymentId) {
        return ledgerEntryRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }
}

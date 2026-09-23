-- Index for fast customer payment history lookups
CREATE INDEX idx_payments_customer_id ON payments (customer_id);

-- Index for payment time-range queries and audits
CREATE INDEX idx_payments_created_at ON payments (created_at DESC);

-- Index for fetching all ledger entries associated with a payment
CREATE INDEX idx_ledger_payment_id ON ledger_entries (payment_id);

-- Index for account-level balance queries and ledger audits
CREATE INDEX idx_ledger_account_id ON ledger_entries (account_id);

-- Partial index optimizing the Outbox Worker polling unpublished events
CREATE INDEX idx_outbox_pending_polling ON outbox_events (created_at ASC)
WHERE status = 'PENDING';

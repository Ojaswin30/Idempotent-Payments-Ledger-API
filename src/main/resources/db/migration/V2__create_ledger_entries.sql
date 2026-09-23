CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ledger_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(id)
        ON DELETE CASCADE
);

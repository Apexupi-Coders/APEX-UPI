-- CBS CREDIT DATABASE SCHEMA (Enhanced)
-- Tracks all credit operations with balance tracking

CREATE TABLE IF NOT EXISTS credit_ledger (
    id            BIGSERIAL PRIMARY KEY,
    txn_id        VARCHAR(64) UNIQUE NOT NULL,
    rrn           VARCHAR(32),
    payee_vpa     VARCHAR(128) NOT NULL,
    payee_bank    VARCHAR(32) NOT NULL,
    payee_account VARCHAR(32),
    amount        NUMERIC(15,2) NOT NULL,
    balance_after NUMERIC(15,2),
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_credit_txn_id ON credit_ledger(txn_id);
CREATE INDEX IF NOT EXISTS idx_credit_status ON credit_ledger(status);
CREATE INDEX IF NOT EXISTS idx_credit_payee  ON credit_ledger(payee_vpa);
CREATE INDEX IF NOT EXISTS idx_credit_date   ON credit_ledger(created_at);

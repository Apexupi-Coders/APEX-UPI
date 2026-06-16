-- CBS DEBIT DATABASE SCHEMA (Enhanced)
-- Tracks all debit operations with balance tracking

CREATE TABLE IF NOT EXISTS debit_ledger (
    id              BIGSERIAL PRIMARY KEY,
    txn_id          VARCHAR(64) UNIQUE NOT NULL,
    rrn             VARCHAR(32),
    payer_vpa       VARCHAR(128) NOT NULL,
    payer_bank      VARCHAR(32) NOT NULL,
    payer_account   VARCHAR(32),
    amount          NUMERIC(15,2) NOT NULL,
    balance_after   NUMERIC(15,2),
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reversal_reason TEXT,
    reversed_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reversal_ledger (
    id          BIGSERIAL PRIMARY KEY,
    txn_id      VARCHAR(64) UNIQUE NOT NULL,
    payer_vpa   VARCHAR(128) NOT NULL,
    amount      NUMERIC(15,2) NOT NULL,
    reason      TEXT,
    reversed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_debit_txn_id ON debit_ledger(txn_id);
CREATE INDEX IF NOT EXISTS idx_debit_status ON debit_ledger(status);
CREATE INDEX IF NOT EXISTS idx_debit_payer  ON debit_ledger(payer_vpa);
CREATE INDEX IF NOT EXISTS idx_debit_date   ON debit_ledger(created_at);

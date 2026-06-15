-- Blacklist table
CREATE TABLE blacklist (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  identifier VARCHAR(100) NOT NULL UNIQUE,
  identifier_type VARCHAR(20) NOT NULL,
  reason TEXT,
  created_at TIMESTAMP DEFAULT now()
);

-- Daily transaction summary (read-only for Rules service)
CREATE TABLE transaction_summary (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payer_vpa VARCHAR(100) NOT NULL,
  txn_id VARCHAR(64) NOT NULL UNIQUE,
  amount NUMERIC(15,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  txn_date DATE NOT NULL,
  created_at TIMESTAMP DEFAULT now()
);

-- Validation audit log
CREATE TABLE validation_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  txn_id VARCHAR(64) NOT NULL,
  payer_vpa VARCHAR(100),
  payee_vpa VARCHAR(100),
  amount NUMERIC(15,2),
  decision VARCHAR(10) NOT NULL,
  reason_code VARCHAR(50),
  evaluated_at TIMESTAMP DEFAULT now()
);

# Data Models and Database Schema

## 1. Overview

The PSP Switch uses PostgreSQL as the primary durable datastore and Redis as a secondary in-memory store for idempotency and velocity controls. This document describes all JPA entity classes, their mapped database tables, column constraints, and data flow through the system.

---

## 2. Redis Data Structures

Redis is used in two services for distinct purposes.

### 2.1 Transaction Orchestrator — Idempotency Cache

| Key Pattern | Type | TTL | Description |
|---|---|---|---|
| `tr::{txnRef}::{payeeVpa}` | String | 3600 seconds | Idempotency slot. Value contains cached JSON response |

Mechanism: Redis `SETNX` (SET-if-Not-eXists). Only the first request with a given key succeeds in setting it. Subsequent requests find the key present and return the cached value.

### 2.2 TPAP Ingress Service — Idempotency Cache

| Key Pattern | Type | TTL | Description |
|---|---|---|---|
| `idempotency::{txnRef}::{payeeVpa}` | String | Configurable (default 3600s) | Cached AcceptedResponse JSON |

### 2.3 Rules Validation Service — Velocity and Duplicate Counters

| Key Pattern | Type | TTL | Description |
|---|---|---|---|
| `velocity::{payerVpa}::{minuteSlot}` | String (counter) | 60 seconds | Per-VPA per-minute transaction count |
| `dup::{txnRef}::{payerVpa}::{payeeVpa}::{amount}` | String | 600 seconds | Short-window duplicate detection flag |

---

## 3. PostgreSQL Schemas

### 3.1 Transaction Orchestrator — `transactions` Table

Owned by the Transaction Orchestrator. Represents the primary saga state for every payment processed by the PSP Switch.

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Auto-increment primary key |
| `tid` | VARCHAR(20) | NOT NULL | PSP transaction ID (`PSP-XXXXXXXX`) |
| `tr` | VARCHAR(255) | NOT NULL | Merchant transaction reference |
| `pa` | VARCHAR(512) | NOT NULL | Payee VPA (AES-256 encrypted) |
| `pn` | VARCHAR(512) | NOT NULL | Payee name (AES-256 encrypted) |
| `mc` | VARCHAR(10) | NOT NULL | Merchant Category Code |
| `am` | DECIMAL(15,2) | NOT NULL | Transaction amount |
| `mam` | DECIMAL(15,2) | NOT NULL | Minimum transaction amount |
| `cu` | VARCHAR(3) | NOT NULL | Currency code |
| `mode` | VARCHAR(5) | NOT NULL | UPI mode (`04`, `05`, `16`) |
| `mid` | VARCHAR(512) | NOT NULL | Merchant ID (AES-256 encrypted) |
| `msid` | VARCHAR(100) | | Merchant store ID |
| `mtid` | VARCHAR(100) | | Merchant terminal ID |
| `state` | VARCHAR(20) | NOT NULL | Saga state enum value |
| `failure_reason` | VARCHAR(1000) | | Populated on FAILED or COMPENSATED |
| `created_at` | TIMESTAMP | NOT NULL | Row creation time |
| `updated_at` | TIMESTAMP | NOT NULL | Last state transition time |

Indexes:
- PRIMARY KEY on `id`
- UNIQUE on `tid`
- INDEX on `(tr, pa)` for composite key lookup
- INDEX on `state` for reconciliation queries

### 3.2 Transaction Orchestrator — `ledger_entries` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(20) | NOT NULL | Reference to `transactions.tid` |
| `pa` | VARCHAR(512) | NOT NULL | Payee VPA (AES-256 encrypted) |
| `amount` | DECIMAL(15,2) | NOT NULL | Ledgered amount |
| `currency` | VARCHAR(3) | NOT NULL | Currency code |
| `direction` | VARCHAR(10) | NOT NULL | `CREDIT` or `DEBIT` |
| `created_at` | TIMESTAMP | NOT NULL | Record creation time |

### 3.3 TPAP Ingress Service — `idempotency_records` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `idempotency_key` | VARCHAR(512) | NOT NULL | Composite key string |
| `txn_id` | VARCHAR(20) | NOT NULL | Assigned PSP transaction ID |
| `response_body` | TEXT | NOT NULL | Serialized AcceptedResponse JSON |
| `created_at` | TIMESTAMP | NOT NULL | Record creation time |
| `expires_at` | TIMESTAMP | NOT NULL | Expiration time (created_at + TTL) |

Indexes:
- UNIQUE on `idempotency_key`

### 3.4 Rules Validation Service — `blacklist` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `vpa` | VARCHAR(255) | NOT NULL | Blacklisted UPI VPA |
| `reason` | VARCHAR(500) | | Reason for blacklisting |
| `added_at` | TIMESTAMP | NOT NULL | Entry creation time |

Indexes:
- INDEX on `vpa` for fast lookup

### 3.5 Rules Validation Service — `transaction_summary` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `vpa` | VARCHAR(255) | NOT NULL | VPA |
| `summary_date` | DATE | NOT NULL | Calendar date of the summary |
| `txn_count` | INTEGER | NOT NULL DEFAULT 0 | Transaction count for the day |
| `txn_volume` | DECIMAL(15,2) | NOT NULL DEFAULT 0.00 | Volume for the day |

Indexes:
- UNIQUE on `(vpa, summary_date)`

### 3.6 Rules Validation Service — `validation_logs` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(50) | NOT NULL | Transaction reference |
| `payer_vpa` | VARCHAR(255) | NOT NULL | Payer VPA |
| `payee_vpa` | VARCHAR(255) | NOT NULL | Payee VPA |
| `amount` | DECIMAL(15,2) | NOT NULL | Amount validated |
| `decision` | VARCHAR(10) | NOT NULL | `APPROVED` or `REJECTED` |
| `failed_rule` | VARCHAR(100) | | Rule name if rejected |
| `rejection_reason` | VARCHAR(500) | | Human-readable rejection reason |
| `evaluated_at` | TIMESTAMP | NOT NULL | Evaluation timestamp |

### 3.7 Ledger Service — `ledger_entries` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(20) | NOT NULL | PSP transaction identifier |
| `payer_vpa` | VARCHAR(512) | NOT NULL | Payer VPA (AES-256 encrypted) |
| `payee_vpa` | VARCHAR(512) | NOT NULL | Payee VPA (AES-256 encrypted) |
| `amount` | DECIMAL(15,2) | NOT NULL | Transaction amount |
| `currency` | VARCHAR(3) | NOT NULL | Currency code |
| `direction` | VARCHAR(10) | NOT NULL | `CREDIT` or `DEBIT` |
| `status` | VARCHAR(20) | NOT NULL | Final transaction status |
| `npci_response_code` | VARCHAR(10) | | NPCI result code |
| `created_at` | TIMESTAMP | NOT NULL | Ledger record creation |

### 3.8 Ledger Service — `txn_status_events` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(20) | NOT NULL | Transaction identifier |
| `event_type` | VARCHAR(50) | NOT NULL | `NPCI_RESPONSE` or `SWITCH_COMPLETED` |
| `status` | VARCHAR(20) | NOT NULL | Status at event time |
| `raw_payload` | TEXT | | Full JSON event payload |
| `created_at` | TIMESTAMP | NOT NULL | Event receipt time |

### 3.9 Ledger Service — `upi_transactions` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(20) | NOT NULL | PSP transaction identifier |
| `txn_ref` | VARCHAR(255) | | Merchant transaction reference |
| `amount` | DECIMAL(15,2) | NOT NULL | Transaction amount |
| `currency` | VARCHAR(3) | NOT NULL | Currency |
| `final_status` | VARCHAR(20) | NOT NULL | Terminal saga state |
| `npci_result` | VARCHAR(10) | | NPCI result code |
| `created_at` | TIMESTAMP | NOT NULL | Record creation |
| `settled_at` | TIMESTAMP | | NPCI settlement timestamp |

### 3.10 PSP Ledger Service — `psp_ledger` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(20) | NOT NULL | Reference transaction |
| `settlement_date` | DATE | NOT NULL | Settlement date |
| `debit_amount` | DECIMAL(15,2) | NOT NULL DEFAULT 0.00 | PSP debit |
| `credit_amount` | DECIMAL(15,2) | NOT NULL DEFAULT 0.00 | PSP credit |
| `entry_type` | VARCHAR(20) | NOT NULL | `PAYMENT`, `REVERSAL`, `FEE` |
| `status` | VARCHAR(20) | NOT NULL | Transaction outcome |
| `created_at` | TIMESTAMP | NOT NULL | Record creation |

### 3.11 Audit Service — `audit_log` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(50) | NOT NULL | Transaction identifier |
| `source` | VARCHAR(100) | NOT NULL | Publishing service |
| `status` | VARCHAR(50) | NOT NULL | Event status |
| `payer` | VARCHAR(255) | | Payer VPA |
| `payee` | VARCHAR(255) | | Payee VPA |
| `amount` | DOUBLE PRECISION | | Transaction amount |
| `stage` | VARCHAR(100) | | Lifecycle stage |
| `remarks` | VARCHAR(1000) | | Remarks |
| `created_at` | TIMESTAMP | NOT NULL | Audit record time |

### 3.12 TPAP Egress Service — `webhook_configs` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `tpap_id` | VARCHAR(100) | NOT NULL | Registered TPAP identifier |
| `event_type` | VARCHAR(50) | NOT NULL | Event type |
| `webhook_url` | VARCHAR(2048) | NOT NULL | Target webhook endpoint |
| `active` | BOOLEAN | NOT NULL DEFAULT true | Delivery active flag |
| `created_at` | TIMESTAMP | NOT NULL | Configuration creation time |
| `updated_at` | TIMESTAMP | | Last update time |

Indexes:
- UNIQUE on `(tpap_id, event_type)`

### 3.13 TPAP Egress Service — `delivery_logs` Table

| Column | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `txn_id` | VARCHAR(20) | NOT NULL | Transaction identifier |
| `tpap_id` | VARCHAR(100) | NOT NULL | Target TPAP |
| `event_type` | VARCHAR(50) | NOT NULL | Event type dispatched |
| `webhook_url` | VARCHAR(2048) | NOT NULL | URL attempted |
| `status` | VARCHAR(20) | NOT NULL | `SUCCESS`, `FAILED`, `SKIPPED` |
| `http_status` | INTEGER | | HTTP response code |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 1 | Delivery attempts made |
| `delivered_at` | TIMESTAMP | | First successful delivery time |
| `created_at` | TIMESTAMP | NOT NULL | Log record creation |

---

## 4. Encryption Overview

| Service | Fields Encrypted | Algorithm | Key Source |
|---|---|---|---|
| Transaction Orchestrator | `pa`, `pn`, `mid` | AES/ECB/PKCS5Padding | `crypto.key` in application.properties |
| Ledger Service | `payer_vpa`, `payee_vpa` | AES/ECB/PKCS5Padding | `crypto.key` in application.properties |


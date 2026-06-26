# Data Models — Banking Switch

## 1. Overview

This document describes all persistent data models (JPA entities and their database tables) and all Kafka event model classes used within the Banking Switch. The two primary data stores are:

- `bankswitch_db` — PostgreSQL database on the Banking Switch VM, owned by the Transaction Orchestrator and the Bank Ledger Service.

---

## 2. JPA Entities

### 2.1 TransactionEntity

**Service:** Transaction Orchestrator
**Table:** `transactions`
**Java class:** `com.bankingswitch.orchestrator.model.entity.TransactionEntity`

Stores the lifecycle state of every inbound UPI transaction.

| Field | Java Type | Column | Constraints | Description |
|---|---|---|---|---|
| `txnId` | String | `txn_id` VARCHAR(255) | PRIMARY KEY | Transaction identifier, extracted from UPI XML |
| `txnType` | String | `txn_type` VARCHAR(50) | | `ReqBalEnq`, `ReqPay`, or `ReqCredit` |
| `state` | TransactionState | `state` VARCHAR(50) | | Current state from the saga state machine |
| `payerVpa` | String | `payer_vpa` VARCHAR(255) | | Payer VPA (nullable, populated when available in XML) |
| `payeeVpa` | String | `payee_vpa` VARCHAR(255) | | Payee VPA (nullable, populated when available in XML) |
| `amount` | Double | `amount` DOUBLE PRECISION | | Transaction amount |
| `xmlPayload` | String | `xml_payload` TEXT | | Full raw UPI XML body |
| `createdAt` | long | `created_at` BIGINT | | Unix epoch milliseconds — record creation time |
| `updatedAt` | long | `updated_at` BIGINT | | Unix epoch milliseconds — last state change time |

#### DDL

```sql
CREATE TABLE transactions (
    txn_id      VARCHAR(255) PRIMARY KEY,
    txn_type    VARCHAR(50),
    state       VARCHAR(50),
    payer_vpa   VARCHAR(255),
    payee_vpa   VARCHAR(255),
    amount      DOUBLE PRECISION,
    xml_payload TEXT,
    created_at  BIGINT,
    updated_at  BIGINT
);
```

---

### 2.2 EventLogEntity

**Service:** Transaction Orchestrator
**Table:** `event_log`
**Java class:** `com.bankingswitch.orchestrator.model.entity.EventLogEntity`

Append-only event history table. One row is written for every state transition of every transaction.

| Field | Java Type | Column | Constraints | Description |
|---|---|---|---|---|
| `id` | Long | `id` BIGINT | SERIAL PRIMARY KEY | Auto-generated entry identifier |
| `txnId` | String | `txn_id` VARCHAR(255) | | Foreign reference to `transactions.txn_id` |
| `eventType` | String | `event_type` VARCHAR(50) | | State name (`RECEIVED`, `CBS_PENDING`, etc.) or `CREATED` |
| `eventData` | String | `event_data` TEXT | | Human-readable description of the state transition |
| `timestamp` | long | `timestamp` BIGINT | | Unix epoch milliseconds of the transition |

#### DDL

```sql
CREATE TABLE event_log (
    id          SERIAL PRIMARY KEY,
    txn_id      VARCHAR(255),
    event_type  VARCHAR(50),
    event_data  TEXT,
    timestamp   BIGINT
);
```

#### Sample Rows — Full Lifecycle of a DEBIT Transaction

| txn_id | event_type | event_data | timestamp |
|---|---|---|---|
| txn-001 | CREATED | Transaction initialized | 1750825200000 |
| txn-001 | CBS_PENDING | Sending DEBIT to CBS | 1750825200100 |
| txn-001 | CBS_SUCCESS | Received CBS response: SUCCESS | 1750825200350 |
| txn-001 | SUCCESS | Transaction completed | 1750825200355 |

---

### 2.3 BankLedger

**Service:** Bank Ledger Service
**Table:** `bank_ledger`
**Java class:** `com.hpe.bank_ledger_service.BankLedger`

Stores the bank-side accounting record of each confirmed financial movement.

| Field | Java Type | Column | Constraints | Description |
|---|---|---|---|---|
| `id` | Long | `id` BIGINT | SERIAL PRIMARY KEY | Auto-generated entry identifier |
| `txnId` | String | `txn_id` VARCHAR(255) | | Transaction identifier |
| `account` | String | `account` VARCHAR(255) | | Account number or VPA |
| `entryType` | String | `entry_type` VARCHAR(50) | | `DEBIT` or `CREDIT` |
| `amount` | Double | `amount` DOUBLE PRECISION | | Amount of the financial movement |
| `status` | String | `status` VARCHAR(50) | | Always `SUCCESS` for entries created by this service |

#### DDL

```sql
CREATE TABLE bank_ledger (
    id          SERIAL PRIMARY KEY,
    txn_id      VARCHAR(255),
    account     VARCHAR(255),
    entry_type  VARCHAR(50),
    amount      DOUBLE PRECISION,
    status      VARCHAR(50)
);
```

---

## 3. Kafka Event Models

### 3.1 TransactionState Enum

**Class:** `com.bankingswitch.orchestrator.model.TransactionState`

| Value | Description |
|---|---|
| `RECEIVED` | Transaction record created; event consumed from Kafka |
| `CBS_PENDING` | CBS operation request published; awaiting response |
| `CBS_SUCCESS` | CBS confirmed the operation as successful |
| `CBS_FAILED` | CBS returned a failure or error |
| `SUCCESS` | Final state; NPCI callback dispatched |
| `FAILED` | Final state; CBS failure or XML parse error |

---

### 3.2 InboundTransactionEvent

**Package:** `com.bankingswitch.listener.model` (produced) / `com.bankingswitch.orchestrator.model` (consumed)
**Topic:** `banking.inbound.txn`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `txnType` | String | `ReqBalEnq`, `ReqPay`, or `ReqCredit` |
| `xmlPayload` | String | Full raw UPI XML |
| `timestamp` | long | Unix epoch milliseconds |

---

### 3.3 CbsRequestEvent

**Package:** `com.bankingswitch.orchestrator.model` (produced) / `com.bankingswitch.cbsadapter.model` (consumed)
**Topic:** `banking.cbs.request`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `operation` | String | `BALANCE`, `DEBIT`, or `CREDIT` |
| `vpa` | String | VPA of the account to operate on |
| `amount` | Double | Amount (null for BALANCE) |
| `xmlPayload` | String | Original UPI XML |

---

### 3.4 CbsResponseEvent

**Package:** `com.bankingswitch.cbsadapter.model` (produced) / `com.bankingswitch.orchestrator.model` (consumed)
**Topic:** `banking.cbs.response`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `operation` | String | Echo of the requested operation |
| `status` | String | `SUCCESS`, `FAILED`, `INSUFFICIENT_FUNDS`, or `CBS_UNAVAILABLE` |
| `errorCode` | String | Error code if not SUCCESS (nullable) |
| `balance` | Double | Account balance for BALANCE operations (nullable) |
| `xmlPayload` | String | Original UPI XML |

---

### 3.5 NpciResponseEvent / NpciCallbackEvent

**Package:** `com.bankingswitch.orchestrator.model` (produced as `NpciResponseEvent`) / `com.bankingswitch.npciadapter.model` (consumed as `NpciCallbackEvent`)
**Topic:** `banking.npci.response`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `txnType` | String | `RespBalEnq`, `RespPay`, or `RespCredit` |
| `status` | String | Final transaction status |
| `errorCode` | String | Error code if applicable (nullable) |
| `balance` | Double | Balance for `RespBalEnq` (nullable) |
| `xmlPayload` | String | Original inbound XML |

---

## 4. Database Summary

| Database | VM | Owner Service | Tables |
|---|---|---|---|
| `bankswitch_db` | Banking Switch VM | Transaction Orchestrator | `transactions`, `event_log` |
| `bankswitch_db` | Banking Switch VM | Bank Ledger Service | `bank_ledger` |

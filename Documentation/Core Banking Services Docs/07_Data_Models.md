# Data Models — Core Banking System

## 1. Overview

The CBS maintains its own PostgreSQL database (`cbs_db`) on the CBS VM. It contains three tables: `accounts`, `transaction_ledger`, and `user_credentials`. This document describes all JPA entities, their database schemas, and the DTOs used by the REST API.

---

## 2. JPA Entities

### 2.1 Account

**Class:** `com.bankingswitch.cbs.model.entity.Account`
**Table:** `accounts`
**Primary Key:** `vpa` (String — the VPA is the unique account identifier)

Stores the core account data for each registered user. The VPA serves as the primary key; no separate surrogate key is used.

| Field | Java Type | Column | Constraints | Description |
|---|---|---|---|---|
| `vpa` | String | `vpa` VARCHAR | PRIMARY KEY | Virtual Payment Address, unique per account |
| `accountNumber` | String | `account_number` VARCHAR | | Linked bank account number |
| `balance` | BigDecimal | `balance` DECIMAL | | Current account balance |
| `status` | String | `status` VARCHAR | | Account status: `ACTIVE`, `FROZEN`, `CLOSED` |
| `pinHash` | String | `pin_hash` VARCHAR | | SHA-256 or bcrypt hash of the account PIN |
| `version` | Long | `version` BIGINT | `@Version` | JPA optimistic locking version counter |

The `@Version` field enables JPA optimistic locking. If two transactions attempt to update the same account row simultaneously using stale version data, the second will fail with an `OptimisticLockException`. In practice, the Debit and Credit services use pessimistic locking (`SELECT ... FOR UPDATE`) which supersedes the optimistic lock mechanism for write operations.

#### DDL

```sql
CREATE TABLE accounts (
    vpa             VARCHAR(255) PRIMARY KEY,
    account_number  VARCHAR(255),
    balance         DECIMAL(19, 4),
    status          VARCHAR(50),
    pin_hash        VARCHAR(255),
    version         BIGINT
);
```

#### Example Row

| vpa | account_number | balance | status | pin_hash | version |
|---|---|---|---|---|---|
| `user@bankdemo` | `ACC100001` | `12500.7500` | `ACTIVE` | `<hash>` | `5` |

---

### 2.2 TransactionLedger

**Class:** `com.bankingswitch.cbs.model.entity.TransactionLedger`
**Table:** `transaction_ledger`
**Primary Key:** `id` (auto-generated)

Immutable financial movement record. One row is written for every committed debit or credit operation. Rows are never updated or deleted.

| Field | Java Type | Column | Constraints | Description |
|---|---|---|---|---|
| `id` | Long | `id` BIGINT | SERIAL PRIMARY KEY | Auto-generated identifier |
| `txnId` | String | `txn_id` VARCHAR | | UPI transaction identifier |
| `vpa` | String | `vpa` VARCHAR | | VPA of the account that was debited or credited |
| `type` | String | `type` VARCHAR | | `DEBIT` or `CREDIT` |
| `amount` | BigDecimal | `amount` DECIMAL | | Amount of the financial movement |
| `balanceBefore` | BigDecimal | `balance_before` DECIMAL | | Account balance before the operation |
| `balanceAfter` | BigDecimal | `balance_after` DECIMAL | | Account balance after the operation |
| `timestamp` | LocalDateTime | `timestamp` TIMESTAMP | | Exact date and time the entry was written |

#### DDL

```sql
CREATE TABLE transaction_ledger (
    id              SERIAL PRIMARY KEY,
    txn_id          VARCHAR(255),
    vpa             VARCHAR(255),
    type            VARCHAR(10),
    amount          DECIMAL(19, 4),
    balance_before  DECIMAL(19, 4),
    balance_after   DECIMAL(19, 4),
    timestamp       TIMESTAMP
);
```

#### Example Rows

| id | txn_id | vpa | type | amount | balance_before | balance_after | timestamp |
|---|---|---|---|---|---|---|---|
| 1 | txn-001 | `user@bankdemo` | DEBIT | 500.00 | 12500.75 | 12000.75 | 2026-06-25 10:00:00 |
| 2 | txn-002 | `payee@bankdemo` | CREDIT | 500.00 | 5000.00 | 5500.00 | 2026-06-25 10:00:01 |

---

### 2.3 UserCredential

**Class:** `com.bankingswitch.cbs.model.entity.UserCredential`
**Table:** `user_credentials`
**Primary Key:** `vpa` (String)

Stores the MPIN hash and device binding for each registered user. This table supports authentication flows and device management.

| Field | Java Type | Column | Constraints | Description |
|---|---|---|---|---|
| `vpa` | String | `vpa` VARCHAR | PRIMARY KEY | Virtual Payment Address |
| `mpinHash` | String | `mpin_hash` VARCHAR | | Hashed MPIN value |
| `deviceId` | String | `device_id` VARCHAR | | Registered device identifier |

#### DDL

```sql
CREATE TABLE user_credentials (
    vpa         VARCHAR(255) PRIMARY KEY,
    mpin_hash   VARCHAR(255),
    device_id   VARCHAR(255)
);
```

---

## 3. REST API DTOs

DTOs (Data Transfer Objects) are used as request and response bodies for the CBS REST API. They are separate from the JPA entities to maintain a clean boundary between the API surface and the persistence layer.

### 3.1 BalanceResponse

**Class:** `com.bankingswitch.cbs.model.dto.BalanceResponse`
**Used by:** GET `/cbs/balance/{vpa}`

| Field | Type | Description |
|---|---|---|
| `vpa` | String | The queried VPA |
| `balance` | BigDecimal | Current balance. Null if account not found. |
| `status` | String | `SUCCESS` or `NOT_FOUND` |

---

### 3.2 DebitRequest

**Class:** `com.bankingswitch.cbs.model.dto.DebitRequest`
**Used by:** POST `/cbs/debit`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | UPI transaction identifier |
| `vpa` | String | VPA of the account to debit |
| `amount` | BigDecimal | Amount to deduct |

---

### 3.3 CreditRequest

**Class:** `com.bankingswitch.cbs.model.dto.CreditRequest`
**Used by:** POST `/cbs/credit`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | UPI transaction identifier |
| `vpa` | String | VPA of the account to credit |
| `amount` | BigDecimal | Amount to add |

---

### 3.4 OperationResponse

**Class:** `com.bankingswitch.cbs.model.dto.OperationResponse`
**Used by:** POST `/cbs/debit` and POST `/cbs/credit`

| Field | Type | Description |
|---|---|---|
| `status` | String | `SUCCESS`, `INSUFFICIENT_FUNDS`, or `FAILED` |
| `balanceBefore` | BigDecimal | Balance before the operation |
| `balanceAfter` | BigDecimal | Balance after the operation |
| `message` | String | Human-readable result description |

---

## 4. Repository Details

### AccountRepository

**Class:** `com.bankingswitch.cbs.repository.AccountRepository`
**Extends:** `JpaRepository<Account, String>`

In addition to standard JPA repository methods, provides:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.vpa = :vpa")
Optional<Account> findByVpaForUpdate(@Param("vpa") String vpa);
```

This method is called by `DebitService` and `CreditService` to acquire a `SELECT ... FOR UPDATE` lock on the account row before performing any balance modification.

### TransactionLedgerRepository

**Class:** `com.bankingswitch.cbs.repository.TransactionLedgerRepository`
**Extends:** `JpaRepository<TransactionLedger, Long>`

Standard JPA repository. Only `save()` is called in normal operation; no custom queries are defined.

### UserCredentialRepository

**Class:** `com.bankingswitch.cbs.repository.UserCredentialRepository`
**Extends:** `JpaRepository<UserCredential, String>`

Standard JPA repository. Used for credential lookup by VPA.

---

## 5. Database Summary

| Database | VM | Tables |
|---|---|---|
| `cbs_db` | CBS VM (Local) / Banking Services VM (Azure) | `accounts`, `transaction_ledger`, `user_credentials` |

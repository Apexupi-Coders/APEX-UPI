# Ledger Service

## 1. Purpose

The Ledger Service provides an internal bookkeeping capability within the CBS. Every time a debit or credit operation successfully modifies an account balance, the calling service invokes `LedgerService.recordTransaction()` to append an immutable record of that financial movement to the `transaction_ledger` table.

The Ledger Service does not expose any REST endpoint. It is a purely internal component invoked by the Debit and Credit Services within their transactional boundaries.

---

## 2. Class

**Class:** `com.bankingswitch.cbs.service.LedgerService`
**Spring Bean:** `@Service`

---

## 3. Dependencies

| Dependency | Type | Description |
|---|---|---|
| `TransactionLedgerRepository` | Spring Data JPA | Persists ledger entries to `transaction_ledger` |

---

## 4. Method: `recordTransaction`

```
recordTransaction(String txnId, String vpa, String type, BigDecimal amount,
                  BigDecimal before, BigDecimal after) : void
```

### Transaction Behaviour

| Attribute | Value |
|---|---|
| `@Transactional` | Yes |
| Propagation | `Propagation.MANDATORY` |

`MANDATORY` propagation means this method **must** be called from within an already-active transaction. If no active transaction exists at the call site, Spring throws an `IllegalTransactionStateException`. This design constraint ensures that a ledger entry is never written independently of the balance-modifying service method. If the outer transaction (Debit or Credit service) rolls back, this ledger entry rolls back with it.

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier for traceability |
| `vpa` | String | VPA of the account involved |
| `type` | String | `DEBIT` or `CREDIT` |
| `amount` | BigDecimal | Amount of the financial movement |
| `before` | BigDecimal | Account balance before the operation |
| `after` | BigDecimal | Account balance after the operation |

### Logic

1. Construct a `TransactionLedger` entity with all provided fields.
2. Set `timestamp = LocalDateTime.now()`.
3. Persist via `TransactionLedgerRepository.save()`.

The method returns void. Failures propagate as exceptions and cause the enclosing transaction to roll back.

---

## 5. `TransactionLedger` Entity

**Class:** `com.bankingswitch.cbs.model.entity.TransactionLedger`
**Table:** `transaction_ledger`

| Field | Java Type | Column | Constraints | Description |
|---|---|---|---|---|
| `id` | Long | `id` BIGINT | SERIAL PRIMARY KEY | Auto-generated entry identifier |
| `txnId` | String | `txn_id` VARCHAR | | UPI transaction identifier |
| `vpa` | String | `vpa` VARCHAR | | VPA of the account |
| `type` | String | `type` VARCHAR | | `DEBIT` or `CREDIT` |
| `amount` | BigDecimal | `amount` DECIMAL | | Amount of the movement |
| `balanceBefore` | BigDecimal | `balance_before` DECIMAL | | Balance immediately before the operation |
| `balanceAfter` | BigDecimal | `balance_after` DECIMAL | | Balance immediately after the operation |
| `timestamp` | LocalDateTime | `timestamp` TIMESTAMP | | Exact time the entry was written |

---

## 6. Design Rationale

The ledger is append-only. No update or delete operations are ever performed on `transaction_ledger` rows. Each row represents an immutable historical fact about a balance change.

The `balanceBefore` and `balanceAfter` fields allow the full balance history of any account to be reconstructed from the ledger table alone, independent of the current value in the `accounts` table. This is useful for reconciliation, dispute resolution, and audit purposes.

The `Propagation.MANDATORY` constraint eliminates an entire class of bugs where a ledger entry might be written without a corresponding committed balance change (or vice versa). It is a deliberate architectural decision, not a framework default.

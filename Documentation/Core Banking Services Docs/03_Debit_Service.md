# Debit Service

## 1. Purpose

The Debit Service executes debit operations against an account in the CBS. It validates that the account has sufficient balance, subtracts the requested amount, persists the updated balance, and records the financial movement in the transaction ledger — all within a single atomic database transaction.

---

## 2. Class

**Class:** `com.bankingswitch.cbs.service.DebitService`
**Spring Bean:** `@Service`

---

## 3. Dependencies

| Dependency | Type | Description |
|---|---|---|
| `AccountRepository` | Spring Data JPA | Retrieves the account with a pessimistic write lock |
| `LedgerService` | Spring Service | Appends a ledger entry within the same transaction |

---

## 4. Method: `debit`

```
debit(DebitRequest request) : OperationResponse
```

### Transaction Behaviour

| Attribute | Value |
|---|---|
| `@Transactional` | Yes |
| Isolation Level | `Isolation.SERIALIZABLE` |
| Read-Only | No |

`SERIALIZABLE` isolation is the highest isolation level. It ensures that concurrent debit requests on the same account execute one at a time with no interleaving, preventing race conditions that could result in the same balance being read by two threads simultaneously, both passing the balance check, and both deducting — causing the account to go negative.

### Pessimistic Write Lock

The account row is retrieved using `AccountRepository.findByVpaForUpdate()`, which executes:

```sql
SELECT a FROM Account a WHERE a.vpa = :vpa
```

with `@Lock(LockModeType.PESSIMISTIC_WRITE)`. This translates to a `SELECT ... FOR UPDATE` at the database level, blocking any other transaction that attempts to read or write the same account row until this transaction commits or rolls back.

### Logic

1. Retrieve the account using the pessimistic write lock. If not found, throw `RuntimeException("Account not found for VPA: {vpa}")`.
2. Read `balanceBefore = account.getBalance()`.
3. If `balanceBefore < request.getAmount()`, return `OperationResponse` with status `"INSUFFICIENT_FUNDS"`. No balance change is made. No ledger entry is written.
4. Compute `balanceAfter = balanceBefore - request.getAmount()`.
5. Set `account.setBalance(balanceAfter)` and save via `AccountRepository.save()`.
6. Call `LedgerService.recordTransaction()` with entry type `"DEBIT"`, the transaction ID, and both balance values.
7. Return `OperationResponse` with status `"SUCCESS"`, `balanceBefore`, and `balanceAfter`.

### Return Type — `OperationResponse`

| Field | Type | Description |
|---|---|---|
| `status` | String | `SUCCESS`, `INSUFFICIENT_FUNDS`, or error description |
| `balanceBefore` | BigDecimal | Account balance before the operation |
| `balanceAfter` | BigDecimal | Account balance after the operation |
| `message` | String | Human-readable result description |

---

## 5. REST Endpoint

| Method | Path | Controller |
|---|---|---|
| POST | `/cbs/debit` | `CbsOperationsController.debit()` |

Any `RuntimeException` thrown by the service is caught by the controller, which returns an `OperationResponse` with `status = "FAILED"` and the exception message.

### Request Body — `DebitRequest`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier for ledger traceability |
| `vpa` | String | VPA of the account to debit |
| `amount` | BigDecimal | Amount to deduct |

### Example Request

```json
POST /cbs/debit
Content-Type: application/json

{
  "txnId": "txn-20260625-001",
  "vpa": "user@bankdemo",
  "amount": 500.00
}
```

### Example Response — Success

```json
{
  "status": "SUCCESS",
  "balanceBefore": 12500.75,
  "balanceAfter": 12000.75,
  "message": "Debit successful"
}
```

### Example Response — Insufficient Funds

```json
{
  "status": "INSUFFICIENT_FUNDS",
  "balanceBefore": 100.00,
  "balanceAfter": 100.00,
  "message": "Not enough balance"
}
```

---

## 6. Concurrency and Safety

The combination of `SERIALIZABLE` transaction isolation and `PESSIMISTIC_WRITE` locking provides the following guarantees:

| Guarantee | Mechanism |
|---|---|
| No double-spend | `SELECT ... FOR UPDATE` blocks concurrent reads of the same row until the transaction commits |
| No dirty reads | SERIALIZABLE prevents reading uncommitted changes from other transactions |
| No phantom reads | SERIALIZABLE prevents new rows from appearing mid-transaction |
| Ledger atomicity | The balance update and ledger write occur in the same transaction; if either fails, both roll back |

# Credit Service

## 1. Purpose

The Credit Service executes credit operations against an account in the CBS. It adds the specified amount to the account balance, persists the updated balance, and records the financial movement in the transaction ledger — all within a single atomic database transaction.

Unlike a debit, a credit does not require a balance check. As long as the account exists, the operation proceeds unconditionally.

---

## 2. Class

**Class:** `com.bankingswitch.cbs.service.CreditService`
**Spring Bean:** `@Service`

---

## 3. Dependencies

| Dependency | Type | Description |
|---|---|---|
| `AccountRepository` | Spring Data JPA | Retrieves the account with a pessimistic write lock |
| `LedgerService` | Spring Service | Appends a ledger entry within the same transaction |

---

## 4. Method: `credit`

```
credit(CreditRequest request) : OperationResponse
```

### Transaction Behaviour

| Attribute | Value |
|---|---|
| `@Transactional` | Yes |
| Isolation Level | `Isolation.SERIALIZABLE` |
| Read-Only | No |

`SERIALIZABLE` isolation is applied for the same reason as the Debit Service: concurrent credit operations on the same account must not interleave in a way that causes ledger inconsistencies or lost updates to the balance field.

### Pessimistic Write Lock

The account row is retrieved using `AccountRepository.findByVpaForUpdate()`, which executes a `SELECT ... FOR UPDATE`. This serializes concurrent writes to the same account row at the database level.

### Logic

1. Retrieve the account using the pessimistic write lock. If not found, throw `RuntimeException("Account not found for VPA: {vpa}")`.
2. Read `balanceBefore = account.getBalance()`.
3. Compute `balanceAfter = balanceBefore + request.getAmount()`.
4. Set `account.setBalance(balanceAfter)` and save via `AccountRepository.save()`.
5. Call `LedgerService.recordTransaction()` with entry type `"CREDIT"`, the transaction ID, and both balance values.
6. Return `OperationResponse` with status `"SUCCESS"`, `balanceBefore`, and `balanceAfter`.

### Return Type — `OperationResponse`

| Field | Type | Description |
|---|---|---|
| `status` | String | `SUCCESS` or error description |
| `balanceBefore` | BigDecimal | Account balance before the credit |
| `balanceAfter` | BigDecimal | Account balance after the credit |
| `message` | String | Human-readable result description |

---

## 5. REST Endpoint

| Method | Path | Controller |
|---|---|---|
| POST | `/cbs/credit` | `CbsOperationsController.credit()` |

Any `RuntimeException` thrown by the service is caught by the controller, which returns an `OperationResponse` with `status = "FAILED"` and the exception message.

### Request Body — `CreditRequest`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier for ledger traceability |
| `vpa` | String | VPA of the account to credit |
| `amount` | BigDecimal | Amount to add |

### Example Request

```json
POST /cbs/credit
Content-Type: application/json

{
  "txnId": "txn-20260625-002",
  "vpa": "payee@bankdemo",
  "amount": 500.00
}
```

### Example Response — Success

```json
{
  "status": "SUCCESS",
  "balanceBefore": 5000.00,
  "balanceAfter": 5500.00,
  "message": "Credit successful"
}
```

### Example Response — Account Not Found

```json
{
  "status": "FAILED",
  "balanceBefore": null,
  "balanceAfter": null,
  "message": "Account not found for VPA: payee@bankdemo"
}
```

---

## 6. Atomicity

The balance update and ledger record are written within the same `@Transactional` boundary. If the ledger write fails, the balance update is also rolled back, and vice versa. This ensures that no credit can be applied to an account without a corresponding ledger entry, and no orphan ledger entry can exist without a corresponding balance change.

`LedgerService.recordTransaction()` is annotated with `@Transactional(propagation = Propagation.MANDATORY)`, meaning it will throw an exception if called outside an active transaction. This enforces at the framework level that a ledger entry is never written independently of a service-layer transaction.

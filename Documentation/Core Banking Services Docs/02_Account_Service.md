# Account Service

## 1. Purpose

The Account Service provides the balance enquiry capability of the Core Banking System. It reads the current balance of an account identified by VPA and returns it to the caller. It does not modify account data in any way.

---

## 2. Class

**Class:** `com.bankingswitch.cbs.service.AccountService`
**Spring Bean:** `@Service`

---

## 3. Dependencies

| Dependency | Type | Description |
|---|---|---|
| `AccountRepository` | Spring Data JPA | Used to look up the Account entity by VPA |

---

## 4. Method: `getBalance`

```
getBalance(String vpa) : BalanceResponse
```

### Transaction Behaviour

| Attribute | Value |
|---|---|
| `@Transactional` | Yes |
| Isolation Level | `Isolation.READ_COMMITTED` |
| Read-Only | `true` |

`READ_COMMITTED` isolation is sufficient for a balance enquiry: it prevents dirty reads (reading uncommitted balance changes from concurrent transactions) while avoiding the overhead of serializable isolation.

### Logic

1. Calls `AccountRepository.findById(vpa)` to retrieve the `Account` entity using the VPA as the primary key.
2. If the account is found, returns a `BalanceResponse` containing the VPA, the current balance, and `status = "SUCCESS"`.
3. If the account is not found, returns a `BalanceResponse` containing the queried VPA, a null balance, and `status = "NOT_FOUND"`.

No exception is thrown for a missing account. The caller receives a structured response with a `NOT_FOUND` status.

### Return Type — `BalanceResponse`

| Field | Type | Description |
|---|---|---|
| `vpa` | String | The queried VPA |
| `balance` | BigDecimal | Current account balance. Null if account not found. |
| `status` | String | `SUCCESS` or `NOT_FOUND` |

---

## 5. REST Endpoint

| Method | Path | Controller |
|---|---|---|
| GET | `/cbs/balance/{vpa}` | `CbsOperationsController.getBalance()` |

The controller passes the `{vpa}` path variable directly to `AccountService.getBalance()` and returns the `BalanceResponse` as a JSON body.

### Example Request

```
GET /cbs/balance/user@bankdemo
```

### Example Response — Account Found

```json
{
  "vpa": "user@bankdemo",
  "balance": 12500.75,
  "status": "SUCCESS"
}
```

### Example Response — Account Not Found

```json
{
  "vpa": "unknown@bankdemo",
  "balance": null,
  "status": "NOT_FOUND"
}
```

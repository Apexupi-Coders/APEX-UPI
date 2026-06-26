# REST API Reference — Core Banking System

## 1. Overview

The CBS exposes three REST endpoints, all mounted under the `/cbs` base path. All requests and responses use `application/json`. The CBS does not use authentication at the HTTP layer; network-level access control is enforced by restricting inbound connections to the CBS Adapter only.

---

## 2. Base Path

```
/cbs
```

All endpoints are served at the configured port of the CBS service on the CBS VM (or Banking Services VM in Azure). No hardcoded addresses are used in the source code; all URLs are constructed from the `cbs.host` property in the CBS Adapter configuration.

---

## 3. Endpoints

### 3.1 GET /cbs/balance/{vpa}

Retrieves the current account balance for the specified VPA.

**Controller method:** `CbsOperationsController.getBalance()`
**Service:** `AccountService.getBalance()`
**Transaction isolation:** `READ_COMMITTED` (read-only)

#### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `vpa` | String | Virtual Payment Address of the account to query |

#### Response — `BalanceResponse`

| Field | Type | Description |
|---|---|---|
| `vpa` | String | The queried VPA |
| `balance` | BigDecimal | Current account balance. Null if account not found. |
| `status` | String | `SUCCESS` or `NOT_FOUND` |

#### HTTP Status Codes

| Condition | HTTP Status |
|---|---|
| Account found | 200 OK |
| Account not found | 200 OK (status field = `NOT_FOUND`) |

#### Examples

**Request:**
```
GET /cbs/balance/user@bankdemo
```

**Response — Found:**
```json
{
  "vpa": "user@bankdemo",
  "balance": 12500.75,
  "status": "SUCCESS"
}
```

**Response — Not Found:**
```json
{
  "vpa": "unknown@bankdemo",
  "balance": null,
  "status": "NOT_FOUND"
}
```

---

### 3.2 POST /cbs/debit

Debits the specified amount from the account identified by VPA. Checks available balance before proceeding.

**Controller method:** `CbsOperationsController.debit()`
**Service:** `DebitService.debit()`
**Transaction isolation:** `SERIALIZABLE` with pessimistic write lock

#### Request Body — `DebitRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `txnId` | String | Yes | UPI transaction identifier for ledger traceability |
| `vpa` | String | Yes | VPA of the account to debit |
| `amount` | BigDecimal | Yes | Amount to deduct |

#### Response — `OperationResponse`

| Field | Type | Description |
|---|---|---|
| `status` | String | `SUCCESS`, `INSUFFICIENT_FUNDS`, or `FAILED` |
| `balanceBefore` | BigDecimal | Balance before the debit |
| `balanceAfter` | BigDecimal | Balance after the debit |
| `message` | String | Human-readable result description |

#### HTTP Status Codes

| Condition | HTTP Status |
|---|---|
| Debit successful | 200 OK |
| Insufficient funds | 200 OK (status field = `INSUFFICIENT_FUNDS`) |
| Account not found | 200 OK (status field = `FAILED`) |
| Unexpected exception | 200 OK (status field = `FAILED`) |

#### Examples

**Request:**
```json
POST /cbs/debit
Content-Type: application/json

{
  "txnId": "txn-20260625-001",
  "vpa": "user@bankdemo",
  "amount": 500.00
}
```

**Response — Success:**
```json
{
  "status": "SUCCESS",
  "balanceBefore": 12500.75,
  "balanceAfter": 12000.75,
  "message": "Debit successful"
}
```

**Response — Insufficient Funds:**
```json
{
  "status": "INSUFFICIENT_FUNDS",
  "balanceBefore": 100.00,
  "balanceAfter": 100.00,
  "message": "Not enough balance"
}
```

**Response — Account Not Found:**
```json
{
  "status": "FAILED",
  "balanceBefore": null,
  "balanceAfter": null,
  "message": "Account not found for VPA: user@bankdemo"
}
```

---

### 3.3 POST /cbs/credit

Credits the specified amount to the account identified by VPA. No balance check is required for a credit operation.

**Controller method:** `CbsOperationsController.credit()`
**Service:** `CreditService.credit()`
**Transaction isolation:** `SERIALIZABLE` with pessimistic write lock

#### Request Body — `CreditRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `txnId` | String | Yes | UPI transaction identifier for ledger traceability |
| `vpa` | String | Yes | VPA of the account to credit |
| `amount` | BigDecimal | Yes | Amount to add |

#### Response — `OperationResponse`

| Field | Type | Description |
|---|---|---|
| `status` | String | `SUCCESS` or `FAILED` |
| `balanceBefore` | BigDecimal | Balance before the credit |
| `balanceAfter` | BigDecimal | Balance after the credit |
| `message` | String | Human-readable result description |

#### HTTP Status Codes

| Condition | HTTP Status |
|---|---|
| Credit successful | 200 OK |
| Account not found | 200 OK (status field = `FAILED`) |
| Unexpected exception | 200 OK (status field = `FAILED`) |

#### Examples

**Request:**
```json
POST /cbs/credit
Content-Type: application/json

{
  "txnId": "txn-20260625-002",
  "vpa": "payee@bankdemo",
  "amount": 500.00
}
```

**Response — Success:**
```json
{
  "status": "SUCCESS",
  "balanceBefore": 5000.00,
  "balanceAfter": 5500.00,
  "message": "Credit successful"
}
```

---

## 4. Error Handling Summary

The CBS controller wraps all service calls in try/catch. No unhandled exceptions propagate to the HTTP response layer. All failure conditions return HTTP 200 with a structured `OperationResponse` containing a `FAILED` status and the exception message.

This design choice keeps the CBS Adapter's error handling simple: it only needs to inspect the `status` field of the JSON response, not HTTP status codes, to determine whether the CBS operation succeeded.

---

## 5. Manual Test Examples

These examples show how to invoke CBS endpoints from the Banking Switch VM during integration testing. Replace `<CBS-VM-IP>` with the actual IP address of the CBS VM and `<port>` with the configured service port.

```bash
# Balance enquiry
curl -X GET http://<CBS-VM-IP>:<port>/cbs/balance/user@bankdemo

# Debit
curl -X POST http://<CBS-VM-IP>:<port>/cbs/debit \
  -H "Content-Type: application/json" \
  -d '{"txnId":"txn-001","vpa":"user@bankdemo","amount":500.00}'

# Credit
curl -X POST http://<CBS-VM-IP>:<port>/cbs/credit \
  -H "Content-Type: application/json" \
  -d '{"txnId":"txn-002","vpa":"payee@bankdemo","amount":500.00}'
```

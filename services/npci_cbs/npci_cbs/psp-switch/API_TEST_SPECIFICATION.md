# UPI Payment System API Test Specification

## Overview
This document provides a high-level specification for testing the UPI Payment System APIs. It defines endpoints, request/response formats, sample interactions, and test scenarios to ensure correct behavior under various conditions.

**Important**: All payment processing is asynchronous. Clients should poll `GET /pay/{txnId}` to track status changes over time. Behavior must be validated solely through API responses.

## API Endpoints

| Method | Endpoint              | Description |
|--------|-----------------------|-------------|
| `POST` | `/pay`                | Initiate a UPI payment transaction. Returns initial txnId for polling. |
| `GET`  | `/pay/{txnId}`        | Retrieve current status of a transaction. |
| `GET`  | `/balance/{upiId}`    | Retrieve current balance for a UPI ID. |

## Request Format
### POST /pay
**Content-Type**: `application/json`

```json
{
  "payer": "payer@upi",
  "payee": "payee@upi",
  "amount": 100.50,
  "idempotencyKey": "unique-client-request-id"
}
```

- `payer` (required, string): UPI ID of the payer.
- `payee` (required, string): UPI ID of the payee.
- `amount` (required, number): Transaction amount (positive decimal).
- `idempotencyKey` (optional, string): Client-provided unique key to prevent duplicates.

## Response Structure

### PaymentResponse (POST /pay, GET /pay/{txnId})
**Content-Type**: `application/json`

```json
{
  "txnId": "txn_12345",
  "status": "SUCCESS",
  "message": "Transaction completed successfully"
}
```

- `txnId` (string): Unique transaction identifier.
- `status` (string): Current status.
- `message` (string): Human-readable description.

**Status Values**:
- `SUCCESS`
- `FAILED`
- `DUPLICATE`
- `COMPENSATED`
- `NOT_FOUND`

### BalanceResponse (GET /balance/{upiId})
```json
{
  "upiId": "user@upi",
  "balance": 1000.75
}
```

## HTTP Status Code Mapping
| HTTP Code | Response Status    | Typical Trigger |
|-----------|--------------------|-----------------|
| 200       | SUCCESS            | Normal completion |
| 400       | ERROR              | Invalid request |
| 404       | NOT_FOUND          | txnId unknown |
| 409       | DUPLICATE          | Idempotent retry |
| 503       | REJECTED           | Temporary issues |

## Sample Requests and Responses

### 1. SUCCESS (Poll after initiation)
**Request**:
```bash
curl -X POST http://localhost:8080/pay \
  -H "Content-Type: application/json" \
  -d '{
    "payer": "10001@apex",
    "payee": "10002@apex",
    "amount": 100.0,
    "idempotencyKey": "req-1"
  }'

# Response: {"txnId": "txn-1", "status": "PENDING", "message": "Processing..."}
# Poll:
curl http://localhost:8080/pay/txn-1
```

**Expected Poll Response**:
```json
{
  "txnId": "txn-1",
  "status": "SUCCESS",
  "message": "Transaction completed successfully"
}
```

### 2. FAILED (Insufficient balance)
**Poll Response**:
```json
{
  "txnId": "txn-2",
  "status": "FAILED",
  "message": "Insufficient balance"
}
```

### 3. DUPLICATE (Same idempotencyKey)
**Response** (immediate 409):
```json
{
  "txnId": "txn-1",
  "status": "DUPLICATE",
  "message": "Duplicate request detected"
}
```

### 4. COMPENSATED (Failure with reversal)
**Poll Response**:
```json
{
  "txnId": "txn-3",
  "status": "COMPENSATED",
  "message": "Transaction compensated"
}
```

## Test Scenarios

| Scenario Name              | Endpoint       | Input Example                                                                 | Expected Output (JSON + HTTP)                                                                 | Description |
|----------------------------|----------------|-------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|-------------|
| Successful transaction     | POST /pay → GET /pay/{txnId} | payer: "10001@apex", payee: "10002@apex", amount: 100.0, idempotencyKey: "unique1" | POST: 200 + PENDING<br>GET: 200 + SUCCESS ("Transaction completed successfully")             | Valid request with sufficient payer balance. Status transitions: PENDING → SUCCESS. Balance updates correctly. |
| Duplicate request          | POST /pay      | Same idempotencyKey as successful txn                                         | 409 + DUPLICATE ("Duplicate request detected"), returns original txnId                       | Same idempotencyKey retries same txn. No new txnId or double debit. |
| Insufficient balance       | POST /pay → GET /pay/{txnId} | payer: "10003@apex" (balance < amount), amount: 200.0                         | POST: 200 + PENDING<br>GET: 200 + FAILED ("Insufficient balance")                             | Payer lacks funds. No balance deduction. Status: FAILED. |
| Failure with compensation  | POST /pay → GET /pay/{txnId} | Simulate mid-process failure (e.g., payee issue)                              | POST: 200 + PENDING<br>GET: 200 + COMPENSATED ("Transaction compensated")                    | Partial failure reversed. Payer balance restored. Status: COMPENSATED. |
| Retry handling             | POST /pay (fail) → POST /pay/{txnId} retry → GET /pay/{txnId} | Initial fail → retry endpoint/action                                           | Initial: FAILED<br>Retry: PENDING → SUCCESS<br>Verify idempotency preserved                  | Failed txn retried successfully. Status transitions observable via polling. No side effects. |
| Non-existent txnId         | GET /pay/{txnId} | Invalid txnId                                                                 | 404 + NOT_FOUND ("Transaction not found")                                                     | Graceful handling of unknown txnId. No errors leaked. |
| Invalid request            | POST /pay      | Missing payer or negative amount                                              | 400 + ERROR ("Invalid request parameters")                                                    | Validation errors return clear messages. |

## Notes
- **Asynchronous Processing**: POST /pay returns immediately with txnId. Poll GET /pay/{txnId} (e.g., every 5s) until final status (SUCCESS/FAILED/COMPENSATED).
- **Status Transitions**: Initial: PENDING → SUCCESS/FAILED/COMPENSATED/DUPLICATE. Validate progression via repeated GET calls.
- **Idempotency**: Use idempotencyKey for safe retries. Same key always returns same txnId and current status.
- **Balance Consistency**: Verify /balance/{upiId} reflects txn outcome (deducted for SUCCESS, unchanged otherwise).
- **Production Testing**: Use realistic UPI IDs, amounts. Monitor for timeouts on long-running txns.
- **Validation**: Test solely via API responses/HTTP codes. No internal state assumptions.

---

*Document Version: 1.0* | *Last Updated: $(date)* | *Ready for test case implementation.*


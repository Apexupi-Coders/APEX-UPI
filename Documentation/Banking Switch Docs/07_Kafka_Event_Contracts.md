# Kafka Event Contracts — Banking Switch

## 1. Overview

All inter-service communication within the Banking Switch is event-driven via Apache Kafka. This document defines every Kafka topic, the service that produces to it, the service that consumes from it, and the complete schema of the event payload for each topic.

These contracts are the authoritative reference for any integration or troubleshooting work involving the Banking Switch message bus.

---

## 2. Topic Registry

| Topic Name | Producer | Consumer | Serialization |
|---|---|---|---|
| `banking.inbound.txn` | NPCI Request Listener | Transaction Orchestrator | JSON |
| `banking.cbs.request` | Transaction Orchestrator | CBS Adapter | JSON |
| `banking.cbs.response` | CBS Adapter | Transaction Orchestrator | JSON |
| `banking.npci.response` | Transaction Orchestrator | NPCI Response Adapter | JSON |
| `upi.cbs.debit.confirm` | External / CBS confirmation | Bank Ledger Service | JSON |
| `upi.cbs.credit.confirm` | External / CBS confirmation | Bank Ledger Service | JSON |

All topics use JSON serialization. Kafka consumer group identifiers are listed in Section 5.

---

## 3. Topic: `banking.inbound.txn`

**Producer:** NPCI Request Listener
**Consumer:** Transaction Orchestrator (group: `bank-orchestrator-group`)

Carries each validated inbound UPI request from NPCI.

### Event Schema — `InboundTransactionEvent`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `txnId` | String | No | Transaction identifier extracted from `<Txn id="...">` in the XML |
| `txnType` | String | No | `ReqBalEnq`, `ReqPay`, or `ReqCredit` |
| `xmlPayload` | String | No | Full raw UPI XML payload as received from NPCI |
| `timestamp` | long | No | Unix epoch milliseconds at time of receipt |

### Example

```json
{
  "txnId": "txn-20260625-001",
  "txnType": "ReqPay",
  "xmlPayload": "<ReqPay><Txn id=\"txn-20260625-001\">...</Txn></ReqPay>",
  "timestamp": 1750825200000
}
```

---

## 4. Topic: `banking.cbs.request`

**Producer:** Transaction Orchestrator
**Consumer:** CBS Adapter (group: `cbs-adapter-group`)

Carries the CBS operation request derived from the inbound UPI event.

### Event Schema — `CbsRequestEvent`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `txnId` | String | No | Transaction identifier |
| `operation` | String | No | `BALANCE`, `DEBIT`, or `CREDIT` |
| `vpa` | String | No | VPA of the account to operate on |
| `amount` | Double | Yes | Amount for DEBIT/CREDIT; null for BALANCE |
| `xmlPayload` | String | No | Original XML, passed through for downstream use |

### Example — DEBIT

```json
{
  "txnId": "txn-20260625-001",
  "operation": "DEBIT",
  "vpa": "user@bankdemo",
  "amount": 500.00,
  "xmlPayload": "<ReqPay>...</ReqPay>"
}
```

### Example — BALANCE

```json
{
  "txnId": "txn-20260625-002",
  "operation": "BALANCE",
  "vpa": "user@bankdemo",
  "amount": null,
  "xmlPayload": "<ReqBalEnq>...</ReqBalEnq>"
}
```

---

## 5. Topic: `banking.cbs.response`

**Producer:** CBS Adapter
**Consumer:** Transaction Orchestrator (group: `bank-orchestrator-group`)

Carries the CBS operation result to the Orchestrator.

### Event Schema — `CbsResponseEvent`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `txnId` | String | No | Transaction identifier |
| `operation` | String | No | Echo of the requested operation: `BALANCE`, `DEBIT`, or `CREDIT` |
| `status` | String | No | `SUCCESS`, `FAILED`, `INSUFFICIENT_FUNDS`, or `CBS_UNAVAILABLE` |
| `errorCode` | String | Yes | Error code from CBS if status is not SUCCESS |
| `balance` | Double | Yes | Account balance; populated for BALANCE operations |
| `xmlPayload` | String | No | Original XML, passed through unchanged |

### Example — Successful DEBIT

```json
{
  "txnId": "txn-20260625-001",
  "operation": "DEBIT",
  "status": "SUCCESS",
  "errorCode": null,
  "balance": null,
  "xmlPayload": "<ReqPay>...</ReqPay>"
}
```

### Example — BALANCE with result

```json
{
  "txnId": "txn-20260625-002",
  "operation": "BALANCE",
  "status": "SUCCESS",
  "errorCode": null,
  "balance": 12500.75,
  "xmlPayload": "<ReqBalEnq>...</ReqBalEnq>"
}
```

### Example — CBS Failure

```json
{
  "txnId": "txn-20260625-003",
  "operation": "DEBIT",
  "status": "INSUFFICIENT_FUNDS",
  "errorCode": "U16",
  "balance": null,
  "xmlPayload": "<ReqPay>...</ReqPay>"
}
```

---

## 6. Topic: `banking.npci.response`

**Producer:** Transaction Orchestrator
**Consumer:** NPCI Response Adapter (group: `npci-response-adapter-group`)

Carries the final transaction result for delivery back to NPCI.

### Event Schema — `NpciResponseEvent` / `NpciCallbackEvent`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `txnId` | String | No | Transaction identifier |
| `txnType` | String | No | `RespBalEnq`, `RespPay`, or `RespCredit` |
| `status` | String | No | `SUCCESS` or failure status from CBS |
| `errorCode` | String | Yes | Error code if the operation failed |
| `balance` | Double | Yes | Account balance for balance enquiry responses |
| `xmlPayload` | String | No | Original inbound XML |

### Example

```json
{
  "txnId": "txn-20260625-001",
  "txnType": "RespPay",
  "status": "SUCCESS",
  "errorCode": null,
  "balance": null,
  "xmlPayload": "<ReqPay>...</ReqPay>"
}
```

---

## 7. Topics: `upi.cbs.debit.confirm` and `upi.cbs.credit.confirm`

**Producer:** External CBS confirmation mechanism
**Consumer:** Bank Ledger Service

These topics carry JSON-encoded confirmation messages for successfully completed debit and credit operations.

### Message Schema (JSON Map)

| Key | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `account` | String | Account number or VPA involved in the operation |
| `entryType` | String | `DEBIT` or `CREDIT` |
| `amount` | Number | Transaction amount as a numeric value |

### Example — Debit Confirmation

```json
{
  "txnId": "txn-20260625-001",
  "account": "user@bankdemo",
  "entryType": "DEBIT",
  "amount": 500.00
}
```

---

## 8. Consumer Group Summary

| Consumer Group ID | Service | Topics Subscribed |
|---|---|---|
| `bank-orchestrator-group` | Transaction Orchestrator | `banking.inbound.txn`, `banking.cbs.response` |
| `cbs-adapter-group` | CBS Adapter | `banking.cbs.request` |
| `npci-response-adapter-group` | NPCI Response Adapter | `banking.npci.response` |
| (configured in application.yml) | Bank Ledger Service | `upi.cbs.debit.confirm`, `upi.cbs.credit.confirm` |

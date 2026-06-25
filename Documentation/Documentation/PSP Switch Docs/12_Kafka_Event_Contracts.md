# Kafka Event Contracts

## 1. Overview

All inter-service communication within the PSP Switch is asynchronous and event-driven via Apache Kafka. This document defines every Kafka topic, its schema, and the complete producer/consumer matrix. No service-to-service synchronous HTTP calls exist for the core payment flow (the Rules Validation Service is the only exception, called synchronously by the Orchestrator).

---

## 2. Kafka Infrastructure

| Parameter | Value |
|---|---|
| Kafka Distribution | Confluent Platform 7.4.0 |
| ZooKeeper | Confluent ZooKeeper 7.4.0 |
| Internal broker listener | `kafka:29092` (internal service network) |
| External listener | Configurable; exposed to application services |
| Auto topic creation | Enabled |
| Replication factor | 1 (single-broker development setup) |
| Log retention | 1 hour (development) |

---

## 3. Topic Catalog

| Topic | Direction | Partitions | Retention | Description |
|---|---|---|---|---|
| `upi.txn.requests` | Ingress -> Orchestrator | 1 | 1h | Payment initiation events from TPAP Ingress |
| `npci.outbound.request` | Orchestrator -> NPCI Adapter | 1 | 1h | Outbound payment requests for NPCI |
| `npci.inbound.response` | NPCI Adapter -> Orchestrator, Ledger | 1 | 1h | NPCI callback results |
| `switch.completed` | Orchestrator -> Ledger, PSP Ledger, Audit, Egress | 1 | 1h | Final transaction outcome |
| `upi.transactions.initiated` | Orchestrator -> Audit | 1 | 1h | Transaction acceptance event |
| `upi.npci.verified` | Orchestrator -> Audit | 1 | 1h | NPCI result event for audit |
| `upi.cbs.debit.confirm` | Orchestrator -> Audit | 1 | 1h | CBS payer debit confirmation |
| `upi.cbs.credit.confirm` | Orchestrator -> Audit | 1 | 1h | CBS payee credit confirmation |
| `upi.cbs.reversal` | Orchestrator -> Audit | 1 | 1h | CBS reversal/compensation event |

---

## 4. Producer and Consumer Matrix

| Topic | Producer | Consumer |
|---|---|---|
| `upi.txn.requests` | TPAP Ingress Service, Transaction Orchestrator (test) | Transaction Orchestrator |
| `npci.outbound.request` | Transaction Orchestrator | NPCI Adapter |
| `npci.inbound.response` | NPCI Adapter, NPCI Response Consumer | Transaction Orchestrator, Ledger Service |
| `switch.completed` | Transaction Orchestrator | TPAP Egress Service, Ledger Service, PSP Ledger Service |
| `upi.transactions.initiated` | Transaction Orchestrator | Audit Service |
| `upi.npci.verified` | Transaction Orchestrator | Audit Service |
| `upi.cbs.debit.confirm` | Transaction Orchestrator | Audit Service |
| `upi.cbs.credit.confirm` | Transaction Orchestrator | Audit Service |
| `upi.cbs.reversal` | Transaction Orchestrator | Audit Service |

---

## 5. Event Schemas

### 5.1 Topic: upi.txn.requests

Published by the TPAP Ingress Service after successful authentication and idempotency check.

**KafkaEventEnvelope** (wrapping `UpiPaymentRequest`):

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "PAYMENT_INITIATE",
  "tpapId": "tpap-demo-01",
  "correlationId": "ORD-001",
  "publishedAt": "2026-06-25T10:00:00Z",
  "payload": {
    "tr": "ORD-001",
    "pa": "merchant@yesbank",
    "pn": "Fresh Mart",
    "mc": "5411",
    "am": 500.00,
    "mam": 100.00,
    "cu": "INR",
    "mode": "16",
    "mid": "MID-001",
    "msid": "STORE-01",
    "mtid": "POS-01",
    "isSignatureVerified": true
  }
}
```

### 5.2 Topic: npci.outbound.request

Published by the Transaction Orchestrator when a transaction is ready to be submitted to NPCI.

**NpciOutboundRequestEvent**:

```json
{
  "txnId": "PSP-AB12CD34",
  "msgId": "550e8400-e29b-41d4-a716-446655440001",
  "type": "PAY",
  "payerVpa": "user@demopsp",
  "payeeVpa": "merchant@okaxis",
  "amount": "500.00",
  "timestamp": "2026-06-25T10:00:01Z"
}
```

Allowed values for `type`: `PAY`, `BALANCE`, `REVERSAL`

### 5.3 Topic: npci.inbound.response

Published by the NPCI Adapter or NPCI Response Consumer upon receiving the NPCI callback.

**NpciInboundResponseEvent**:

```json
{
  "txnId": "PSP-AB12CD34",
  "msgId": "550e8400-e29b-41d4-a716-446655440001",
  "type": "PAY",
  "result": "SUCCESS",
  "balance": null,
  "currency": "INR",
  "errCode": "",
  "timestamp": "2026-06-25T10:00:03Z"
}
```

Allowed values for `result`: `SUCCESS`, `FAILURE`, `TIMEOUT`, `DEEMED`

For balance enquiry responses, the `balance` field is populated:

```json
{
  "txnId": "PSP-EF56GH78",
  "msgId": "...",
  "type": "BALANCE",
  "result": "SUCCESS",
  "balance": "25000.00",
  "currency": "INR",
  "errCode": "",
  "timestamp": "2026-06-25T10:00:05Z"
}
```

### 5.4 Topic: switch.completed

Published by the Transaction Orchestrator when the saga reaches a terminal state (SUCCESS, FAILED, or COMPENSATED).

**SwitchCompletedEvent**:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440002",
  "eventType": "PAYMENT_PUSH",
  "tpapId": "tpap-demo-01",
  "txnId": "PSP-AB12CD34",
  "correlationId": "ORD-001",
  "completedAt": "2026-06-25T10:00:05Z",
  "status": "SUCCESS",
  "amount": 500.00,
  "currency": "INR",
  "payerVpa": "user@demopsp",
  "payeeVpa": "merchant@okaxis",
  "failureReason": null
}
```

Allowed values for `eventType`: `PAYMENT_PUSH`, `BALANCE_INQUIRY`, `VPA_VERIFICATION`

Allowed values for `status`: `SUCCESS`, `FAILED`, `COMPENSATED`

### 5.5 Audit Topics (Common Schema)

All audit-stage topics (`upi.transactions.initiated`, `upi.npci.verified`, `upi.cbs.debit.confirm`, `upi.cbs.credit.confirm`, `upi.cbs.reversal`) share a common JSON schema consumed by the Audit Service:

```json
{
  "txnId": "PSP-AB12CD34",
  "source": "transaction-orchestrator",
  "status": "SUBMITTED",
  "payer": "user@demopsp",
  "payee": "merchant@okaxis",
  "amount": 500.00,
  "stage": "NPCI_SUBMIT",
  "remarks": "NPCI request dispatched"
}
```

---

## 6. Consumer Group Assignments

| Consumer Group | Topic | Service |
|---|---|---|
| `orchestrator-group` | `upi.txn.requests` | Transaction Orchestrator |
| `orchestrator-npci-group` | `npci.inbound.response` | Transaction Orchestrator |
| `npci-adapter-group` | `npci.outbound.request` | NPCI Adapter |
| `ledger-npci-group` | `npci.inbound.response` | Ledger Service |
| `ledger-switch-group` | `switch.completed` | Ledger Service |
| `psp-ledger-group` | `switch.completed` | PSP Ledger Service |
| `tpap-egress-group` | `switch.completed` | TPAP Egress Service |
| `audit-group` | All audit stage topics | Audit Service |

---

## 7. Message Key Strategy

| Topic | Message Key | Purpose |
|---|---|---|
| `upi.txn.requests` | `txnRef` | Ensures all events for a request go to same partition |
| `npci.outbound.request` | `txnId` | Ordered processing per transaction |
| `npci.inbound.response` | `txnId` | Ordered resolution per transaction |
| `switch.completed` | `txnId` | Ordered final event per transaction |
| Audit topics | `txnId` | Ordered audit trail per transaction |

---

## 8. Serialization

All Kafka messages use JSON serialization and deserialization with Jackson `ObjectMapper`. The key serializer is `StringSerializer`. The value serializer is `StringSerializer` (JSON string). Consumers deserialize using `StringDeserializer` and then invoke Jackson `readValue()` explicitly.

# Banking Switch — System Overview

## 1. Purpose

The Banking Switch is the bank-side processing layer of the APEX-UPI platform. It receives inbound UPI transaction requests from the PSP Switch via NPCI and is responsible for authenticating, routing, and executing debit, credit, and balance enquiry operations against the Core Banking System (CBS). It also returns structured XML responses to NPCI once each operation completes.

The Banking Switch operates entirely on internal event-driven communication and exposes no direct APIs to the PSP Switch or end users. All coordination with the PSP Switch is mediated through the NPCI network layer.

---

## 2. Position in the APEX-UPI Architecture

```
PSP Switch
    |
    | (UPI XML over HTTP — NPCI protocol)
    v
NPCI Network
    |
    | POST /bank/upi/ReqBalEnq
    | POST /bank/upi/ReqPay
    | POST /bank/upi/ReqCredit
    v
Banking Switch — NPCI Request Listener
    |
    | (Kafka: banking.inbound.txn)
    v
Banking Switch — Transaction Orchestrator
    |
    | (Kafka: banking.cbs.request)
    v
Banking Switch — CBS Adapter
    |
    | (HTTP REST)
    v
Core Banking System (CBS)
    |
    | (HTTP response)
    v
Banking Switch — CBS Adapter
    |
    | (Kafka: banking.cbs.response)
    v
Banking Switch — Transaction Orchestrator
    |
    | (Kafka: banking.npci.response)
    v
Banking Switch — NPCI Response Adapter
    |
    | (HTTP callback to NPCI)
    v
NPCI Network
```

---

## 3. Component Inventory

The Banking Switch is composed of five independently deployable Spring Boot microservices plus one ledger service:

| Service | Artifact ID | Role |
|---|---|---|
| NPCI Request Listener | `npci-request-listener` | Receives inbound UPI XML requests from NPCI. Validates, parses, and publishes to Kafka. |
| Transaction Orchestrator | `orchestrator` | Central saga coordinator. Routes events to CBS and back to NPCI. Persists state in PostgreSQL. |
| CBS Adapter | `cbs-adapter` | Translates Kafka CBS request events into HTTP REST calls against the CBS service. |
| NPCI Response Adapter | `npci-response-adapter` | Consumes completed transaction events from Kafka and sends XML callbacks to NPCI. |
| Bank Ledger Service | `bank-ledger-service` | Maintains a local bank-side ledger of confirmed debit and credit operations. |

---

## 4. Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Messaging | Apache Kafka |
| Persistence | PostgreSQL (Orchestrator and Bank Ledger Service) |
| HTTP Client | Spring RestTemplate |
| Build Tool | Apache Maven |
| Boilerplate Reduction | Project Lombok |
| XML Processing | Jackson Dataformat XML |

---

## 5. Communication Patterns

### 5.1 NPCI to Banking Switch (Inbound)

NPCI sends UPI XML payloads over HTTP POST to the NPCI Request Listener. Supported message types are:

| UPI Message Type | HTTP Path | Description |
|---|---|---|
| `ReqBalEnq` | `POST /bank/upi/ReqBalEnq` | Balance enquiry request |
| `ReqPay` | `POST /bank/upi/ReqPay` | Payment debit request (Payer bank role) |
| `ReqCredit` | `POST /bank/upi/ReqCredit` | Credit request (Payee bank role) |

The listener returns a synchronous XML Acknowledgement (`<Ack>`) to NPCI immediately upon receipt and then publishes the event asynchronously to Kafka.

### 5.2 Internal (Kafka-based)

All inter-service coordination within the Banking Switch uses Kafka topics. No service calls another service over HTTP directly, except the CBS Adapter, which calls the external CBS REST API.

### 5.3 Banking Switch to NPCI (Outbound Callback)

After a transaction is completed by CBS, the NPCI Response Adapter constructs a UPI-compliant XML response and sends it to NPCI via HTTP callback. This closes the transaction lifecycle from the Banking Switch perspective.

---

## 6. Kafka Topic Summary

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `banking.inbound.txn` | NPCI Request Listener | Transaction Orchestrator | Carries parsed inbound UPI events |
| `banking.cbs.request` | Transaction Orchestrator | CBS Adapter | Carries CBS operation requests (BALANCE / DEBIT / CREDIT) |
| `banking.cbs.response` | CBS Adapter | Transaction Orchestrator | Carries CBS operation results |
| `banking.npci.response` | Transaction Orchestrator | NPCI Response Adapter | Carries completed transaction results for NPCI callback |
| `upi.cbs.debit.confirm` | CBS / external confirmation | Bank Ledger Service | Triggers bank-side ledger entry for debits |
| `upi.cbs.credit.confirm` | CBS / external confirmation | Bank Ledger Service | Triggers bank-side ledger entry for credits |

---

## 7. Transaction Types and Bank Role

The Banking Switch participates in two distinct roles depending on the transaction type:

| Scenario | UPI Message | Bank Role | CBS Operation |
|---|---|---|---|
| Payer bank processes outgoing payment | `ReqPay` | Debit the payer's account | DEBIT |
| Payee bank processes incoming credit | `ReqCredit` | Credit the payee's account | CREDIT |
| Any bank handles balance enquiry | `ReqBalEnq` | Read the account balance | BALANCE |

---

## 8. Transaction State Machine

The Transaction Orchestrator tracks each transaction through the following states:

| State | Meaning |
|---|---|
| `RECEIVED` | Inbound event consumed from Kafka; transaction record created |
| `CBS_PENDING` | CBS operation request has been published; awaiting CBS response |
| `CBS_SUCCESS` | CBS confirmed the operation completed successfully |
| `CBS_FAILED` | CBS reported a failure (insufficient funds, account not found, etc.) |
| `SUCCESS` | Final terminal state; NPCI callback has been dispatched |
| `FAILED` | Final terminal state; CBS failure or XML parse error |

---

## 9. Database

The Banking Switch maintains its own PostgreSQL database (`bankswitch_db`) on the Banking Switch VM. No other system writes to this database.

| Table | Owned By | Purpose |
|---|---|---|
| `transactions` | Transaction Orchestrator | Stores transaction records and their current state |
| `event_log` | Transaction Orchestrator | Append-only event history for each transaction |
| `bank_ledger` | Bank Ledger Service | Confirmed debit and credit entries for bank-side accounting |

---

## 10. Deployment

Refer to [09_Configuration_and_Deployment.md](./09_Configuration_and_Deployment.md) for full VM provisioning, configuration, and startup instructions.

### Local Deployment (VirtualBox)

The APEX-UPI system is deployed across four separate virtual machines when running locally:

| VM | Services Hosted |
|---|---|
| VM 1 — PSP Switch | All PSP Switch microservices, Kafka broker, PostgreSQL for PSP |
| VM 2 — Banking Switch | All Banking Switch microservices, PostgreSQL for bankswitch_db |
| VM 3 — Core Banking System | CBS service, PostgreSQL for cbs_db |
| VM 4 — NPCI | NPCI simulator service |

### Cloud Deployment (Azure)

In the Azure cloud deployment, services are consolidated into three virtual machines:

| VM | Services Hosted |
|---|---|
| VM 1 — PSP Switch | All PSP Switch microservices, Kafka, PostgreSQL for PSP |
| VM 2 — NPCI Network | NPCI simulator/network service |
| VM 3 — Banking Services | Banking Switch microservices + CBS service, shared PostgreSQL |

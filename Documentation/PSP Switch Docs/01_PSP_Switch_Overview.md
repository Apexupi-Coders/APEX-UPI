# PSP Switch — System Overview

## 1. Introduction

APEX-UPI is a reference implementation of a Unified Payments Interface (UPI) Payment Service Provider (PSP) Switch. It demonstrates a production-grade, microservice-based architecture that participates in the NPCI UPI 2.x payment network.

A PSP Switch is the technical entity that intermediates between a Third-Party Application Provider (TPAP) — such as a mobile banking application or merchant payment application — and the National Payments Corporation of India (NPCI) interbank settlement network. The PSP Switch is responsible for:

- Receiving payment initiation requests from TPAPs
- Applying regulatory and business rules validation
- Orchestrating the multi-step UPI payment flow
- Communicating with NPCI using the prescribed XML messaging protocol
- Crediting payee accounts via the Core Banking System (CBS)
- Recording all financial movements in an immutable ledger
- Delivering asynchronous results back to the originating TPAP
- Maintaining a compliance-grade audit trail of every event

---

## 2. Bounded Context

The APEX-UPI repository contains three distinct bounded contexts. This documentation covers only the PSP Switch context.

| Context | Directory | Description |
|---|---|---|
| PSP Switch | `services/psp-switch/` | The subject of this document set |
| Banking Switch | `services/banking-switch/` | Bank-side CBS adapter and orchestrator |
| NPCI / CBS Bridge | `services/npci/`, `services/npci-cbs/`, `services/cbs/` | Network-level bridges |

---

## 3. System Architecture

The PSP Switch is composed of nine independently deployable Spring Boot microservices. Services communicate via Apache Kafka for all internal asynchronous operations. The TPAP Ingress Service is the only component that accepts inbound HTTP from external parties. The NPCI Adapter is the only component that makes outbound HTTP calls to NPCI.

The flow of a payment through the system proceeds as follows:

A TPAP submits a payment request to the TPAP Ingress Service over HTTP. The Ingress Service authenticates the request, enforces idempotency, and publishes the request as a Kafka event. The Transaction Orchestrator consumes that event and executes a 10-step saga covering validation, state persistence, NPCI submission, CBS credit, ledger recording, and finalization. NPCI communication is handled entirely by the NPCI Adapter, which translates between the internal Kafka event schema and the NPCI XML protocol. Once the saga reaches a terminal state, downstream services — the Ledger Service, PSP Ledger Service, and Audit Service — consume the completion event and record their respective entries. The TPAP Egress Service delivers the final outcome to the TPAP via an outbound HTTP webhook.

### Service Interaction Summary

| From | To | Transport | Description |
|---|---|---|---|
| TPAP | TPAP Ingress Service | HTTP REST | Payment initiation |
| TPAP Ingress Service | Transaction Orchestrator | Kafka | Normalized payment event |
| Transaction Orchestrator | NPCI Adapter | Kafka | Outbound NPCI request |
| NPCI (external) | NPCI Response Consumer | HTTP (XML) | NPCI callback |
| NPCI Response Consumer | Transaction Orchestrator | Kafka | Parsed NPCI result |
| Transaction Orchestrator | Ledger Service | Kafka | Completion event |
| Transaction Orchestrator | PSP Ledger Service | Kafka | Completion event |
| Transaction Orchestrator | Audit Service | Kafka | Stage events |
| Transaction Orchestrator | TPAP Egress Service | Kafka | Completion event |
| TPAP Egress Service | TPAP | HTTP | Webhook delivery |
| Transaction Orchestrator | Rules Validation Service | HTTP | Synchronous validation |

---

## 4. Payment Flow — End to End

### 4.1 Initiation Phase (Synchronous)

1. The TPAP sends an HTTP POST request to the TPAP Ingress Service with a `PaymentInitiateRequest`.
2. The Ingress Service authenticates the TPAP token.
3. An idempotency check is performed against Redis using a composite key of `(txnRef, payeeVpa)`. If a duplicate is detected, the previous response is returned immediately without re-processing.
4. The request is serialized into a `KafkaEventEnvelope` and published to the `upi.txn.requests` topic.
5. An `AcceptedResponse` (HTTP 202) is returned to the TPAP.

### 4.2 Orchestration Phase (Asynchronous — Steps 1-10)

6. The Transaction Orchestrator consumes from `upi.txn.requests` and begins the 10-step saga.

### 4.3 NPCI Communication Phase

7. The Orchestrator publishes an outbound event to `npci.outbound.request`.
8. The NPCI Adapter consumes the event, builds an XML-formatted `ReqPay` message, signs it, and dispatches it over HTTPS to the NPCI endpoint.
9. NPCI acknowledges receipt synchronously.
10. NPCI fires a `RespPay` HTTP callback to the NPCI Adapter's registered webhook endpoint.
11. The NPCI Response Consumer receives the XML callback, parses it, and publishes a structured event to `npci.inbound.response`.
12. The Orchestrator consumes the NPCI result and advances the saga.

### 4.4 CBS Credit Phase

13. Upon NPCI success, the Orchestrator calls the CBS Adapter to credit the payee account.
14. If CBS credit succeeds, the transaction reaches `SUCCESS` state.
15. If CBS credit fails after NPCI success, a compensation flow initiates — the Orchestrator sends an NPCI reversal and marks the transaction as `COMPENSATED`.

### 4.5 Finalization Phase

16. On final state resolution, the Orchestrator publishes a `switch.completed` event to Kafka.
17. The Ledger Service, PSP Ledger Service, and Audit Service each consume this event and record their respective entries.
18. The TPAP Egress Service consumes the event and delivers the result via HTTP webhook to the TPAP's registered callback URL.

---

## 5. Transaction State Machine

Every transaction managed by the PSP Switch progresses through a finite set of states. The state is persisted in PostgreSQL and cached in memory for saga operations.

| State | Description |
|---|---|
| PENDING | Transaction accepted and written to database; NPCI call pending |
| SUBMITTED | NPCI request dispatched; awaiting NPCI response |
| SUCCESS | NPCI success confirmed; CBS credit complete; ledger written |
| FAILED | NPCI rejected the transaction; or validation failure |
| UNKNOWN | No NPCI response received within the 5-second timeout window |
| COMPENSATED | CBS credit failed after NPCI success; reversal sent to NPCI |

### State Transitions

| From State | Trigger | To State |
|---|---|---|
| PENDING | NPCI request dispatched (Step 6) | SUBMITTED |
| SUBMITTED | NPCI success response received | CBS Credit step |
| SUBMITTED | NPCI failure response received | FAILED |
| SUBMITTED | No response within 5 seconds | UNKNOWN |
| CBS Credit step | CBS credit succeeds | SUCCESS |
| CBS Credit step | CBS credit fails after NPCI success | COMPENSATED |
| UNKNOWN | Reconciliation sweep: NPCI re-query returns success | SUCCESS |
| UNKNOWN | Reconciliation sweep: NPCI re-query returns failure | FAILED |

---

## 6. Infrastructure Requirements

| Component | Purpose | Protocol |
|---|---|---|
| Apache Kafka | Asynchronous inter-service event bus | TCP |
| Apache ZooKeeper | Kafka broker coordination | TCP |
| PostgreSQL | Transactional state and ledger persistence | JDBC |
| Redis | Idempotency key storage and velocity counters | TCP |

---

## 7. Security Considerations

| Concern | Current Implementation | Production Upgrade Path |
|---|---|---|
| TPAP Authentication | Bearer token validation in TpapAuthFilter | OAuth 2.0 client credentials with short-lived JWTs |
| NPCI Transport | Plain HTTP to mock server | mTLS with NPCI-issued PKCS12 certificate |
| NPCI Request Signing | SHA-256 hex | ECDSA P-256 with PSP private key stored in HSM |
| MPIN Encryption | Base64 encoding | AES-256-GCM with NPCI public key |
| PII Encryption at Rest | AES/ECB/PKCS5Padding | AES/GCM/NoPadding with per-record IV and KMS key rotation |
| Idempotency (NPCI Adapter) | In-process ConcurrentHashMap | Redis SET for multi-instance safety |

---

## 8. Repository Structure

```
services/psp-switch/
├── tpap-ingress-service/       HTTP entry point for TPAPs
├── transaction-orchestrator/   10-step saga coordinator
├── npci-adapter/               NPCI network bridge
├── npci-response-consumer/     NPCI XML callback receiver
├── rules-validation-service/   Pre-authorization rules engine
├── ledger-service/             Immutable financial ledger
├── psp-ledger-service/         PSP-level fund movement ledger
├── audit-service/              Compliance audit event consumer
└── tpap-egress-service/        Outbound webhook dispatcher
```

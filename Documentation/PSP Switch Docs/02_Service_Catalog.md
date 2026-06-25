# PSP Switch — Service Catalog

## Overview

The PSP Switch is composed of nine independently deployable microservices. Each service is a Spring Boot application packaged as a Docker container. Services communicate exclusively via Apache Kafka (asynchronous events) with the exception of the TPAP Ingress Service, which also accepts inbound HTTP, and the NPCI Adapter, which makes outbound HTTP calls to NPCI.

---

## Service Summary Table

| Service | Artifact ID | Group ID | Java | Spring Boot | Port | Primary Role |
|---|---|---|---|---|---|---|
| TPAP Ingress Service | `tpap-ingress-service` | `com.pspswitch` | 17 | 3.3.0 | 8080 | Authenticated HTTP gateway for TPAPs |
| Transaction Orchestrator | `transaction-orchestrator` | `com.pspswitch.orchestrator` | 17 | 3.2.x | 8080/8081 | 10-step saga coordinator |
| NPCI Adapter | `npci-adapter` | `com.psp.npci` | 17 | 3.2.5 | 8081/8082 | NPCI network bridge |
| NPCI Response Consumer | `npci-response-consumer` | `com.pspswitch` | 17 | 3.2.5 | 8084 | NPCI XML callback receiver |
| Rules Validation Service | `rules-validation-service` | `com.pspswitch` | 17 | 3.1.5 | 8085 | Pre-authorization rules engine |
| Ledger Service | `ledger-service` | `com.pspswitch` | 17 | 3.2.5 | 8083 | Immutable financial ledger |
| PSP Ledger Service | `psp-ledger-service` | `com.hpe` | 21 | 3.5.15 | 8086 | PSP-level fund movement tracking |
| Audit Service | `audit-service` | `com.audit` | 17 | 3.2.5 | 8087 | Compliance audit trail |
| TPAP Egress Service | `tpap-egress-service` | `com.pspswitch` | 21 | 3.3.0 | 8085 | Outbound webhook dispatcher |

---

## Detailed Service Descriptions

### 1. TPAP Ingress Service

- **Directory:** `services/psp-switch/tpap-ingress-service/`
- **Role:** Receives payment, balance inquiry, and VPA lookup requests from registered TPAPs over HTTP. Validates the bearer token, applies idempotency controls, normalizes the request into a Kafka event envelope, and publishes to the internal Kafka bus. Returns HTTP 202 (Accepted) immediately.
- **Inbound:** HTTP POST from TPAPs
- **Outbound:** Kafka topic `upi.txn.requests`
- **State:** Stateful (Redis for idempotency, PostgreSQL for idempotency records)

### 2. Transaction Orchestrator

- **Directory:** `services/psp-switch/transaction-orchestrator/`
- **Role:** The central coordinator of the UPI payment flow. Implements a 10-step saga with explicit state transitions. Manages NPCI communication delegation, CBS credit delegation, timeout detection (UNKNOWN state), and periodic reconciliation of unresolved transactions.
- **Inbound:** Kafka topic `upi.txn.requests` (from Ingress) and `npci.inbound.response` (from NPCI Adapter)
- **Outbound:** Kafka topic `npci.outbound.request`, direct webhook calls to self for CBS/NPCI simulation, `switch.completed`
- **State:** Stateful (PostgreSQL for durable state, Redis for idempotency, in-memory ConcurrentHashMap for saga speed)

### 3. NPCI Adapter

- **Directory:** `services/psp-switch/npci-adapter/`
- **Role:** The sole service that communicates with the NPCI external network. Consumes outbound requests from the Orchestrator via Kafka, builds XML-formatted UPI messages, signs them (SHA-256 in demo; ECDSA-P256 in production), and dispatches them to NPCI over HTTPS. Receives NPCI callbacks at registered REST endpoints and relays parsed results back to Kafka.
- **Inbound:** Kafka topic `npci.outbound.request`; REST webhook from NPCI (`/upi/RespPay/...`, `/upi/ReqPay/...`)
- **Outbound:** Kafka topic `npci.inbound.response`; REST calls to NPCI
- **State:** Stateless (in-memory idempotency guard only)

### 4. NPCI Response Consumer

- **Directory:** `services/psp-switch/npci-response-consumer/`
- **Role:** Receives raw XML callbacks from NPCI via HTTP, parses them using `NpciXmlParser`, constructs structured `NpciInboundResponseEvent` objects, and publishes them to the `npci.inbound.response` Kafka topic. Acts as the boundary translator from the NPCI XML protocol to the internal event schema.
- **Inbound:** HTTP POST from NPCI (XML payloads)
- **Outbound:** Kafka topic `npci.inbound.response`
- **State:** Stateless

### 5. Rules Validation Service

- **Directory:** `services/psp-switch/rules-validation-service/`
- **Role:** Provides a synchronous REST endpoint for pre-authorization transaction validation. Executes a chain of rules including amount limits, VPA format, blacklist checks, daily limits, velocity controls, and duplicate detection. Results are logged to `validation_logs` table.
- **Inbound:** HTTP POST `/rules/validate` (from Orchestrator or internal callers)
- **Outbound:** HTTP response (`ValidationResponse`), PostgreSQL writes
- **State:** Stateful (PostgreSQL for blacklists and transaction summaries, Redis for velocity rate limiting)

### 6. Ledger Service

- **Directory:** `services/psp-switch/ledger-service/`
- **Role:** Maintains an immutable double-entry ledger of all UPI transactions. Consumes from both `npci.inbound.response` and `switch.completed` Kafka topics. Encrypts sensitive fields before persistence using AES-256. Exposes a query API for ledger lookup.
- **Inbound:** Kafka topics `npci.inbound.response` and `switch.completed`
- **Outbound:** HTTP GET `/ledger/**` (query endpoints); PostgreSQL writes
- **State:** Stateful (PostgreSQL, Flyway migrations)

### 7. PSP Ledger Service

- **Directory:** `services/psp-switch/psp-ledger-service/`
- **Role:** Tracks PSP-level financial positions and fund movements in real-time. Consumes Kafka events from the `switch.completed` topic. Maintains the `psp_ledger` table with debit/credit entries categorized by settlement direction and transaction outcome.
- **Inbound:** Kafka topic `switch.completed`
- **Outbound:** PostgreSQL writes; HTTP query endpoints
- **State:** Stateful (PostgreSQL)

### 8. Audit Service

- **Directory:** `services/psp-switch/audit-service/`
- **Role:** Provides a compliance-grade audit trail by consuming multiple Kafka topics and persisting an event log for every stage of a transaction's lifecycle. The audit log is append-only and stores the full event context including payer, payee, amount, stage, status, and remarks.
- **Inbound:** Kafka topics: `upi.transactions.initiated`, `upi.npci.verified`, `upi.cbs.debit.confirm`, `upi.cbs.credit.confirm`, `upi.cbs.reversal`
- **Outbound:** PostgreSQL writes to `audit_log` table
- **State:** Stateful (PostgreSQL)

### 9. TPAP Egress Service

- **Directory:** `services/psp-switch/tpap-egress-service/`
- **Role:** Delivers final transaction outcomes to registered TPAPs via outbound HTTP webhook. Consumes `switch.completed` events, routes them to the appropriate typed handler (payment push, balance inquiry, VPA verification), looks up the registered TPAP webhook URL from `webhook_configs` table, and performs an HTTP POST with retry logic (up to 3 times on 5xx or timeout; no retry on 4xx).
- **Inbound:** Kafka topic `switch.completed`
- **Outbound:** HTTP POST to registered TPAP webhook endpoints; PostgreSQL writes to `delivery_logs`
- **State:** Stateful (PostgreSQL for webhook config and delivery logs)

---

## Service Interaction Matrix

| From | To | Transport | Topic / Endpoint |
|---|---|---|---|
| TPAP | Ingress Service | HTTP | POST /payment/initiate, /balance, /vpa |
| Ingress Service | Orchestrator | Kafka | `upi.txn.requests` |
| Orchestrator | NPCI Adapter | Kafka | `npci.outbound.request` |
| NPCI (external) | NPCI Response Consumer | HTTP | POST /npci/callback |
| NPCI Response Consumer | Orchestrator | Kafka | `npci.inbound.response` |
| Orchestrator | Ledger Service | Kafka | `switch.completed` |
| Orchestrator | PSP Ledger Service | Kafka | `switch.completed` |
| Orchestrator | Audit Service | Kafka | `upi.transactions.initiated`, stage topics |
| Orchestrator | Egress Service | Kafka | `switch.completed` |
| Egress Service | TPAP | HTTP | POST to registered webhook URL |
| Orchestrator | Rules Validation | HTTP | POST /rules/validate |

# Design Decisions

## 1. Purpose

This document records the key architectural and engineering decisions made during the design of the APEX-UPI PSP Switch. Each decision is presented with its context, the rationale behind the choice, the trade-offs accepted, and the recommended upgrade path for production deployment.

These records serve as the reference point for understanding why the system is built the way it is, and what would need to change for a production-grade rollout.

---

## 2. Architectural Decision Records

### ADR-001 — Saga Pattern for Payment Orchestration

**Decision:** The Transaction Orchestrator implements a 10-step distributed saga rather than a two-phase commit or a single monolithic transaction.

**Rationale:**
A UPI payment involves multiple external systems (NPCI, CBS, Ledger) that do not share a common transaction manager. Two-phase commit is impractical across external HTTP and Kafka boundaries. The saga pattern allows each step to succeed or fail independently, with compensating transactions defined for rollback scenarios.

**Trade-offs accepted:**
- Eventual consistency rather than immediate consistency
- The system may briefly hold a transaction in the UNKNOWN state
- Compensation logic (NPCI reversal on CBS failure) adds implementation complexity

**Production considerations:**
A saga log table should be introduced to record each completed step and enable idempotent replay of compensation actions in the event of an Orchestrator restart mid-saga.

---

### ADR-002 — Asynchronous HTTP Response (HTTP 202 Accepted)

**Decision:** The payment initiation endpoint returns HTTP 202 immediately after writing the PENDING state to PostgreSQL, before the NPCI call is made.

**Rationale:**
NPCI communication is inherently asynchronous — NPCI does not return a final result in the same HTTP call. Holding the HTTP connection open for the full payment duration (potentially several seconds) is not viable for TPAP integrations. The 202 response pattern is the standard approach for long-running financial operations.

**Trade-offs accepted:**
- TPAPs must implement polling or accept a webhook callback rather than reading the result from the initial response
- Additional complexity in TPAP integration

**Production considerations:**
The TPAP Egress Service's webhook delivery removes the need for polling in well-integrated TPAPs. Polling via `GET /api/v1/txn/{txnId}` remains available as a fallback.

---

### ADR-003 — Redis SETNX for Idempotency

**Decision:** Idempotency is enforced using Redis `SETNX` (Set-If-Not-Exists) with a composite key of `{txnRef}::{payeeVpa}` and a one-hour TTL.

**Rationale:**
Financial systems must prevent duplicate processing of retried requests. Redis `SETNX` is an atomic operation that eliminates the race condition inherent in read-then-write patterns. It operates at sub-millisecond latency and is horizontally scalable across multiple Orchestrator instances.

**Trade-offs accepted:**
- Idempotency window is limited to the Redis TTL (1 hour); a retry after 1 hour would be treated as a new transaction
- Redis restart without RDB persistence would lose in-flight idempotency keys

**Production considerations:**
Enable Redis RDB or AOF persistence. Back the idempotency key with a PostgreSQL `idempotency_records` table (already implemented in the Ingress Service) for durability across Redis failures.

---

### ADR-004 — PostgreSQL + In-Memory ConcurrentHashMap Dual Write

**Decision:** The Transaction Orchestrator maintains saga state in both PostgreSQL (via JPA) and an in-process `ConcurrentHashMap` keyed by transaction ID.

**Rationale:**
PostgreSQL writes provide durability and survive process restarts. The in-memory map provides microsecond-level access for within-saga lookups (e.g., WebhookController reading state set by the Orchestrator thread). Without the in-memory map, every saga step would require a database round-trip, adding latency to the hot path.

**Trade-offs accepted:**
- In-memory state is lost on Orchestrator restart; any in-flight saga would need to be recovered from PostgreSQL
- Not safe for multiple Orchestrator instances sharing the same saga — the in-memory map is per-process

**Production considerations:**
Replace the ConcurrentHashMap with a shared Redis cache (L2 cache pattern). This enables horizontal scaling of the Orchestrator with shared in-progress state.

---

### ADR-005 — AES-256 Encryption for PII at Rest

**Decision:** The `pa` (payee VPA), `pn` (payee name), and `mid` (merchant ID) fields are encrypted using AES before every PostgreSQL write and decrypted after every read. The Redis cache stores plaintext.

**Rationale:**
UPI VPAs and merchant identifiers are personally identifiable information (PII) and commercially sensitive data. Regulatory frameworks (RBI guidelines, DPDPA) require PII to be protected at rest. Encrypting at the application layer before writing to PostgreSQL provides protection even if the database file system is accessed directly.

**Trade-offs accepted:**
- AES/ECB mode (used in demo) is not semantically secure — identical plaintexts produce identical ciphertexts. This is acceptable for demo but unacceptable in production.
- Decrypt-on-read adds a small CPU overhead on every read

**Production considerations:**
Switch to AES/GCM/NoPadding with a randomly generated per-record Initialization Vector (IV) stored alongside the ciphertext. Manage the encryption key in Azure Key Vault or an HSM with automatic rotation policy. The `DataCryptoService` interface is already designed to swap the implementation without changing callers.

---

### ADR-006 — Kafka for Internal Service Communication

**Decision:** All inter-service communication within the PSP Switch (except the Rules Validation Service synchronous call) is asynchronous via Apache Kafka.

**Rationale:**
Kafka provides durable, ordered, replayable event streams. Services can be independently scaled, restarted, or upgraded without tight coupling to one another. The consumer group model allows multiple independent consumers (Ledger, Audit, Egress) to each receive a copy of every event without the producer being aware of them.

**Trade-offs accepted:**
- Operational complexity of running Kafka + ZooKeeper
- Debugging event flows requires Kafka tooling (consumer group lag monitoring)
- Message ordering is guaranteed only within a partition

**Production considerations:**
Adopt a schema registry (Confluent Schema Registry with Avro or JSON Schema) to enforce message contracts. Add dead-letter topics for messages that fail deserialization or processing after retry exhaustion. Monitor consumer group lag as a key operational metric.

---

### ADR-007 — Polymorphic Handler Pattern in TPAP Egress Service

**Decision:** The TPAP Egress Service resolves the appropriate webhook handler using an `EventHandlerFactory` that maps `EventType` to a `WebhookEventHandler` implementation, with no conditional dispatch (no if-else or switch).

**Rationale:**
Conditional dispatch (if eventType == PAYMENT_PUSH ... else if ...) becomes a maintenance liability as the number of event types grows. The polymorphic pattern means adding a new event type requires only adding a new `@Component` class that implements `WebhookEventHandler` — no existing code is modified. This adheres to the Open/Closed Principle.

**Trade-offs accepted:**
- Slightly less obvious flow for a new developer reading the code; they must look for all implementations of the interface
- Spring dependency injection at startup must resolve all handlers correctly

**Production considerations:**
No change required — this is already the correct production pattern.

---

### ADR-008 — Webhook Retry Logic (3 attempts, no retry on 4xx)

**Decision:** The TPAP Egress Service retries webhook delivery up to 3 times on HTTP 5xx responses or connection timeouts. It does not retry on 4xx responses.

**Rationale:**
5xx errors indicate a transient server-side failure on the TPAP webhook endpoint that may resolve on retry. 4xx errors (Bad Request, Unauthorized, Not Found) indicate a configuration or payload problem that will not resolve by retrying — retrying would only delay detection of the real issue.

**Trade-offs accepted:**
- 3 retries may be insufficient for long TPAP outages; the delivery attempt is marked FAILED after exhaustion
- No exponential backoff is implemented in the current version

**Production considerations:**
Implement exponential backoff with jitter (e.g., 1s, 2s, 4s). Add a background retry sweep for FAILED deliveries within a configurable time window. Expose TPAP delivery failure counts as a metric for alerting.

---

### ADR-009 — @Scheduled(fixedDelay) for Reconciliation

**Decision:** The `ReconciliationService` uses `@Scheduled(fixedDelay = 60000)` rather than `fixedRate`.

**Rationale:**
`fixedDelay` starts the next execution only after the previous one completes. `fixedRate` would start the next execution on schedule regardless of whether the previous one has finished. For a database-querying reconciliation sweep, overlapping executions could result in the same UNKNOWN transactions being processed twice concurrently, causing race conditions.

**Trade-offs accepted:**
- If a reconciliation sweep takes longer than 60 seconds (e.g., many UNKNOWN transactions), the next sweep is delayed rather than started on time

**Production considerations:**
Use ShedLock to coordinate reconciliation across multiple Orchestrator instances — only one instance should run the sweep at a time. Make the delay and NPCI re-query timeout configurable via `application.properties`.

---

### ADR-010 — NPCI Adapter as the Sole NPCI-Facing Service

**Decision:** Only the NPCI Adapter communicates with NPCI. No other service makes outbound calls to NPCI, and NPCI never interacts with Kafka.

**Rationale:**
This design enforces a clear protocol boundary. The NPCI UPI XML protocol is complex and requires specific signing, encryption, and XML schema conformance. Centralizing this complexity in one service means that changes to the NPCI protocol (e.g., new XSD version, key rotation) require changes in exactly one place. It also simplifies mTLS configuration — only one service needs the NPCI certificate material.

**Trade-offs accepted:**
- The NPCI Adapter becomes a critical path component; its availability directly affects payment success rates

**Production considerations:**
Deploy at least two NPCI Adapter instances behind a load balancer for high availability. The in-memory idempotency guard must be replaced with a Redis SET to prevent duplicate NPCI publishes when multiple instances process the same NPCI retry callback simultaneously.

---

## 3. Summary Table

| ADR | Decision | Key Driver |
|---|---|---|
| ADR-001 | Saga pattern for orchestration | External system boundaries; no shared transaction manager |
| ADR-002 | HTTP 202 Accepted response | NPCI async nature; TPAP UX |
| ADR-003 | Redis SETNX idempotency | Atomic duplicate prevention; horizontal scalability |
| ADR-004 | Dual-write PostgreSQL + ConcurrentHashMap | Durability + saga-speed reads |
| ADR-005 | AES-256 PII encryption at rest | Regulatory compliance (RBI, DPDPA) |
| ADR-006 | Kafka for inter-service events | Loose coupling; independent scalability; replayability |
| ADR-007 | Polymorphic handler in Egress | Open/Closed Principle; extensibility |
| ADR-008 | 3-retry, no-retry-on-4xx webhook | Correct failure semantics for transient vs permanent errors |
| ADR-009 | fixedDelay reconciliation | Prevent overlapping sweeps |
| ADR-010 | NPCI Adapter as sole NPCI boundary | Protocol isolation; mTLS centralization |

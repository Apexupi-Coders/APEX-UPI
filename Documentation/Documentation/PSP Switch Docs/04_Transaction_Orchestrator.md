# Transaction Orchestrator

## 1. Purpose

The Transaction Orchestrator is the central coordination engine of the APEX-UPI PSP Switch. It implements a 10-step distributed saga that manages the complete lifecycle of a UPI payment transaction. It persists transaction state to PostgreSQL, caches saga context in memory for high-speed access, enforces Redis-based idempotency, delegates NPCI and CBS operations to their respective adapters, handles timeouts, and executes periodic reconciliation of transactions that reach an indeterminate state.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `transaction-orchestrator` |
| Group ID | `com.pspswitch.orchestrator` |
| Java Version | 17 |
| Spring Boot Version | 3.2.x |
| Default Port | 8080 (development) / 8081 (Docker Compose cloud) |
| Base Package | `com.pspswitch.orchestrator` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | REST endpoint serving |
| spring-boot-starter-data-jpa | PostgreSQL persistence |
| postgresql | JDBC driver |
| spring-boot-starter-data-redis | Redis for idempotency |
| spring-kafka | Kafka event consumption and publication |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Testing framework |
| spring-kafka-test | Embedded Kafka for tests |
| h2 | In-memory DB for tests |

---

## 4. Source Structure

```
com.pspswitch.orchestrator
├── OrchestratorApplication.java
├── config/
│   └── AsyncConfig.java                @EnableAsync, @EnableScheduling, 10-thread pool
├── controller/
│   ├── TransactionController.java      POST /api/v1/txn, GET /api/v1/txn/{id}, GET /api/v1/txn/ref
│   ├── WebhookController.java          POST /api/v1/webhook/npci, POST /api/v1/webhook/cbs
│   └── ControlController.java          Demo toggles, reconciliation trigger, Kafka test
├── orchestrator/
│   └── TransactionOrchestrator.java    10-step saga with timeout scheduler
├── service/
│   ├── IdempotencyService.java         Redis SETNX, 1-hour TTL
│   ├── TransactionStateService.java    JPA + ConcurrentHashMap dual-write + crypto
│   ├── ValidationService.java          9 sequential validation rules
│   ├── ModePreprocessingService.java   UPI mode 04/05/16 interpretation
│   ├── DataCryptoService.java          AES-256 PII encryption at rest
│   └── ReconciliationService.java      @Scheduled UNKNOWN state resolver
├── adapter/
│   ├── NpciAdapter.java                Mock NPCI: forward, reversal, queryStatus
│   ├── CbsAdapter.java                 Mock CBS: creditPayee with failure toggle
│   ├── LedgerService.java              PostgreSQL ledger with crypto
│   └── NotificationService.java        Mock push notifications
├── kafka/
│   ├── PaymentRequestConsumer.java     @KafkaListener on upi.txn.requests
│   └── PaymentRequestProducer.java     Test message publisher
├── model/
│   ├── TransactionState.java           Enum: PENDING/SUBMITTED/SUCCESS/FAILED/UNKNOWN/COMPENSATED
│   ├── UpiPaymentRequest.java          Request DTO (BigDecimal amounts)
│   ├── TransactionContext.java         Mutable saga context object
│   ├── TransactionResponse.java        Response DTO
│   ├── TransactionEntity.java          JPA entity -> transactions table
│   ├── LedgerEntity.java               JPA entity -> ledger_entries table
│   ├── PreprocessingContext.java        Mode flags (requiresPasscode, flowType)
│   ├── NpciCallbackPayload.java        NPCI webhook DTO
│   └── CbsCallbackPayload.java         CBS webhook DTO
├── repository/
│   ├── TransactionRepository.java      findByTrAndPa, findByState, countByState
│   └── LedgerRepository.java           CRUD for ledger_entries
└── exception/
    ├── ValidationException.java        Custom exception with reason field
    └── GlobalExceptionHandler.java     400/500 error responses
```

---

## 5. Infrastructure

| Component | Purpose | Port |
|---|---|---|
| PostgreSQL 15 | Transaction state and ledger persistence | 5432 |
| Redis 7 | Idempotency cache (atomic SETNX) | 6379 |
| Apache Kafka | Consuming payment requests from Ingress | 9092 |
| Spring Boot | Application runtime | 8080 |

---

## 6. The 10-Step Saga

The saga is implemented in `TransactionOrchestrator.java`. Steps 1-5 execute synchronously in the thread receiving the HTTP or Kafka event. Steps 6-10 execute asynchronously via the Spring `@Async` executor pool (10 threads, configured in `AsyncConfig`).

| Step | Name | Execution | What Happens |
|---|---|---|---|
| 1 | Idempotency Check | Synchronous | Redis SETNX on composite key `{tr}::{pa}`. If key exists, returns cached 200 response with `X-Idempotent-Replayed: true` header |
| 2 | TID Generation | Synchronous | Generates internal transaction ID: `PSP-` + 8-character uppercase alphanumeric UUID segment |
| 3 | Mode Preprocessing | Synchronous | Interprets UPI mode codes: `04` (collect), `05` (QR), `16` (intent). Sets `requiresPasscode` and `flowType` flags in `PreprocessingContext` |
| 4 | Validation | Synchronous | Executes 9 sequential validation rules via `ValidationService` |
| 5 | Write PENDING | Synchronous | Persists `TransactionEntity` with state=PENDING to PostgreSQL; writes to ConcurrentHashMap cache. HTTP 202 is returned to caller at this point |
| 6 | NPCI REST Call | Asynchronous | Calls `NpciAdapter.forward()`. 800ms simulated delay. Updates state to SUBMITTED. A 5-second timeout scheduler is armed |
| 7 | NPCI Webhook | Asynchronous | Receives callback via `WebhookController.handleNpciCallback()`. If `responseCode=00` then success; otherwise failure. State moves to SUCCESS path or FAILED |
| 8 | CBS Credit | Asynchronous | Calls `CbsAdapter.creditPayee()`. 500ms simulated delay. Attempts to credit payee account |
| 9 | Ledger Write | Asynchronous | Calls `LedgerService.record()`. Writes a ledger entry to `ledger_entries` table with PII-encrypted fields |
| 10 | Finalise | Asynchronous | Sets state=SUCCESS (or COMPENSATED on CBS failure). Caches response in Redis. Calls `NotificationService.notify()` |

### Failure Paths

**NPCI Failure (Step 7):** If NPCI returns a non-success response code (e.g., `ZM`), the saga moves directly to FAILED state. A `failureReason` string is persisted.

**5-Second NPCI Timeout:** A `ScheduledExecutorService.schedule()` call is made at Step 6. If the NPCI webhook has not arrived within 5 seconds, the transaction state is updated from SUBMITTED to UNKNOWN.

**CBS Failure — Compensation (Step 8):** If the CBS credit call fails after NPCI has already confirmed success, the system faces a financial inconsistency. The Orchestrator calls `NpciAdapter.reversal()` to request NPCI to reverse the credit, then sets state=COMPENSATED. The `failureReason` records `"CBS credit failed — reversal sent to NPCI"`.

---

## 7. API Endpoints

### Transaction Endpoints

| Method | Endpoint | Description | Success Response |
|---|---|---|---|
| POST | `/api/v1/txn` | Initiate a new payment | 202 (new) or 200 (duplicate, with `X-Idempotent-Replayed: true`) |
| GET | `/api/v1/txn/{txnId}` | Get state by internal TID | 200 with `TransactionResponse` |
| GET | `/api/v1/txn/ref?tr={tr}&pa={pa}` | Look up by composite key | 200 with `TransactionResponse` |

### Webhook Endpoints (Internal)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/webhook/npci` | Receives NPCI callback; advances saga Steps 7-10 |
| POST | `/api/v1/webhook/cbs` | Receives CBS confirmation (informational only in demo) |

### Control Endpoints (Demo / Testing)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/control/npci-failure?enabled=true` | Arm NPCI failure mode (all NPCI calls return failure) |
| POST | `/api/v1/control/cbs-failure?enabled=true` | Arm CBS failure mode (all CBS calls return failure) |
| POST | `/api/v1/control/npci-timeout?enabled=true` | Suppress NPCI webhook (simulate timeout, trigger UNKNOWN) |
| POST | `/api/v1/control/kafka-publish-test` | Publish a test message to the `upi.txn.requests` topic |
| GET | `/api/v1/control/reconcile-now` | Manually trigger one reconciliation sweep immediately |
| GET | `/api/v1/control/status` | Show toggle states and transaction counts per state |

---

## 8. Request Payload

`UpiPaymentRequest` fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `tr` | String | Yes | Transaction reference (merchant order ID) |
| `pa` | String | Yes | Payee VPA (UPI ID) — PII encrypted at rest |
| `pn` | String | Yes | Payee name — PII encrypted at rest |
| `mc` | String | Yes | Merchant Category Code |
| `am` | BigDecimal | Yes | Transaction amount. Must be >= `mam`. Uses BigDecimal for financial precision |
| `mam` | BigDecimal | Yes | Minimum amount |
| `cu` | String | Yes | Currency code. Must be `INR` |
| `mode` | String | Yes | UPI mode code: `04`, `05`, or `16` |
| `mid` | String | Yes | Merchant ID — PII encrypted at rest |
| `msid` | String | Yes | Merchant store ID |
| `mtid` | String | Yes | Merchant terminal ID |
| `isSignatureVerified` | Boolean | Yes | Must be `true` for processing to proceed |

---

## 9. Validation Rules

The `ValidationService` executes 9 rules in sequential order. The first failure terminates validation and returns HTTP 400 with the failure reason.

| Rule | Validation |
|---|---|
| 1 | `am` must not be null |
| 2 | `cu` must equal `INR` |
| 3 | `pa` must not be null or blank |
| 4 | `pa` must match VPA regex pattern (`[a-zA-Z0-9._-]+@[a-zA-Z]+`) |
| 5 | `pn` must not be null or blank |
| 6 | `mc` must not be null or blank |
| 7 | `mode` must be one of `04`, `05`, `16` |
| 8 | `isSignatureVerified` must be `true` |
| 9 | `am` must be >= `mam` |

---

## 10. Services — Internal Details

### 10.1 IdempotencyService

- **Key format:** `tr::{tr}::{pa}` (Redis key)
- **Operation:** Jedis `SETNX` — atomically sets the key only if absent
- **TTL:** 1 hour (3600 seconds)
- **Duplicate behavior:** Returns HTTP 200 with `X-Idempotent-Replayed: true` header and the stored `TransactionResponse` from the Redis value field
- **Note:** Cache stores plaintext data; only PostgreSQL columns are AES-encrypted

### 10.2 DataCryptoService — AES-256 PII Encryption

Encrypts three fields before every PostgreSQL write and decrypts after every read.

| Field | Encrypted |
|---|---|
| `pa` (payee VPA) | Yes |
| `pn` (payee name) | Yes |
| `mid` (merchant ID) | Yes |

- **Algorithm:** AES/ECB/PKCS5Padding (demo). Production: AES/GCM/NoPadding with per-record IV + KMS key rotation
- **Key Source:** Configured via `application.properties` property `crypto.key`
- **Safety Contract:** `encrypt()` and `decrypt()` never throw exceptions. On any error, the original plaintext value is returned, preventing data loss

### 10.3 TransactionStateService — Dual Write

Maintains consistency between the durable PostgreSQL store and the in-process saga cache.

- **Write path:** Calls `transactionRepository.save(entity)` (JPA), then puts the mutable `TransactionContext` into a `ConcurrentHashMap<String, TransactionContext>` keyed by `txnId`
- **Read path:** Cache consulted first for saga operations; PostgreSQL used for API queries by `tr+pa` composite key
- **Crypto touchpoints:** Encrypt before JPA save, decrypt after JPA read

### 10.4 ReconciliationService — Scheduled Sweep

- **Schedule:** `@Scheduled(fixedDelay = 60000)` — runs every 60 seconds after the previous sweep completes. `fixedDelay` (not `fixedRate`) prevents sweep overlap if a sweep takes longer than 60 seconds
- **Logic:** Queries `transactionRepository.findByState(TransactionState.UNKNOWN)`, calls `NpciAdapter.queryStatus(txnId)` for each, and resolves to SUCCESS or FAILED based on the response
- **Manual trigger:** `GET /api/v1/control/reconcile-now` returns `{ "unknownBefore": N, "resolved": M, "unknownAfter": K }`

### 10.5 ModePreprocessingService

Interprets the UPI `mode` field into operational flags used downstream.

| Mode | requiresPasscode | flowType |
|---|---|---|
| `04` | true | COLLECT |
| `05` | false | QR_STATIC |
| `16` | false | INTENT |

---

## 11. Kafka Integration

### Consumer — PaymentRequestConsumer

- **Topic:** `upi.txn.requests`
- **Annotation:** `@KafkaListener(topics = "upi.txn.requests")`
- **Behavior:** Deserializes the `UpiPaymentRequest` JSON payload and invokes the orchestrator's `process()` method, which begins the 10-step saga from Step 1

### Producer — PaymentRequestProducer

Used exclusively by the `POST /api/v1/control/kafka-publish-test` control endpoint to publish a test message directly to the Kafka topic, allowing demonstration of the Kafka integration path without the Ingress Service.

---

## 12. Database Schema

### `transactions` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `tid` | VARCHAR | PSP transaction ID (e.g., `PSP-AB12CD34`) |
| `tr` | VARCHAR | Merchant transaction reference |
| `pa` | VARCHAR | Payee VPA (AES-256 encrypted) |
| `pn` | VARCHAR | Payee name (AES-256 encrypted) |
| `mc` | VARCHAR | Merchant Category Code |
| `am` | DECIMAL | Amount |
| `mam` | DECIMAL | Minimum amount |
| `cu` | VARCHAR | Currency |
| `mode` | VARCHAR | UPI mode |
| `mid` | VARCHAR | Merchant ID (AES-256 encrypted) |
| `msid` | VARCHAR | Merchant store ID |
| `mtid` | VARCHAR | Merchant terminal ID |
| `state` | VARCHAR | Current saga state |
| `failureReason` | VARCHAR | Reason if FAILED or COMPENSATED |
| `createdAt` | TIMESTAMP | Record creation time |
| `updatedAt` | TIMESTAMP | Last state change time |

### `ledger_entries` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `txnId` | VARCHAR | Reference to `transactions.tid` |
| `pa` | VARCHAR | Payee VPA (AES-256 encrypted) |
| `amount` | DECIMAL | Amount ledgered |
| `currency` | VARCHAR | Currency code |
| `direction` | VARCHAR | `CREDIT` or `DEBIT` |
| `createdAt` | TIMESTAMP | Record creation time |

---

## 13. Async Configuration — AsyncConfig

- **Enables:** `@EnableAsync`, `@EnableScheduling`
- **Thread pool:** `ThreadPoolTaskExecutor` with core pool size 10, max pool size 10, queue capacity 100
- **Executor bean name:** `taskExecutor`
- All `@Async` annotated methods in the saga use this executor

---

## 14. Test Coverage

| Test Class | Tests | Scope |
|---|---|---|
| `OrchestratorIntegrationTest` | 12 | End-to-end saga tests with H2 and embedded Redis/Kafka |
| `ValidationServiceTest` | 15 | All 9 rules and edge cases |
| `IdempotencyServiceTest` | 8 | Redis SETNX, TTL, duplicate detection |
| `ModePreprocessingServiceTest` | 8 | All three mode codes and invalid mode handling |

---

## 15. Design Decisions

| Decision | Rationale | Production Upgrade |
|---|---|---|
| `BigDecimal` for amounts | Financial precision; never float or double | Same |
| Redis SETNX for idempotency | Sub-millisecond atomic operation; survives restarts; horizontally scalable | Add TTL monitoring; Redis cluster mode |
| JPA + ConcurrentHashMap dual-write | PostgreSQL for durability; HashMap for saga-speed reads | Add Redis L2 cache |
| AES/ECB for PII encryption | Demo simplicity | AES/GCM/NoPadding + per-record IV + KMS key rotation |
| `@Scheduled(fixedDelay)` | Prevents reconciliation sweep stacking | Distribute via ShedLock for multi-instance |
| Kafka consumer | Event-driven decoupling from Ingress | Add dead-letter topic (DLT) |
| `volatile boolean` toggles | Thread-safe without locks for simple demo flags | Feature flags service |
| Webhook simulation | Adapters call WebhookController directly | Real HTTP calls to external NPCI/CBS endpoints |
| 5-second NPCI timeout -> UNKNOWN | Prevents indefinite SUBMITTED state | Configurable timeout per transaction type + alerting |
| Compensation on CBS failure | Prevents financial inconsistency (NPCI credited, CBS not) | Saga log table for idempotent compensation replay |

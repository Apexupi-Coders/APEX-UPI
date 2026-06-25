# Ledger Service

## 1. Purpose

The Ledger Service maintains an immutable, double-entry financial ledger of all UPI transactions processed by the PSP Switch. It consumes events from Kafka, applies AES-256 encryption to sensitive fields before persistence, and exposes a query API for ledger lookups. It provides the authoritative historical record of all fund movements and NPCI settlement outcomes.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `ledger-service` |
| Group ID | `com.pspswitch` |
| Version | `1.0.0` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8083 |
| Base Package | `com.pspswitch.ledger` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | REST query endpoints |
| spring-boot-starter-data-jpa | PostgreSQL persistence |
| postgresql | JDBC driver |
| flyway-core | Database schema migrations |
| spring-kafka | Kafka event consumption |
| spring-boot-starter-actuator | Health checks |
| jackson-databind | JSON deserialization |
| jackson-datatype-jsr310 | Java 8 date/time serialization support |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Testing |
| spring-kafka-test | Embedded Kafka for tests |

---

## 4. Source Structure

```
com.pspswitch.ledger
├── LedgerServiceApplication.java
├── config/
│   └── KafkaConfig.java                 ConsumerFactory and KafkaListenerContainerFactory
├── consumer/
│   ├── NpciResponseEventConsumer.java   @KafkaListener on npci.inbound.response
│   └── SwitchCompletedEventConsumer.java @KafkaListener on switch.completed
├── controller/
│   └── LedgerQueryController.java       GET /ledger/entry/{txnId}, GET /ledger/entries
├── entity/
│   ├── LedgerEntry.java                 JPA entity -> ledger_entries table
│   ├── TxnStatusEvent.java              JPA entity -> txn_status_events table
│   └── UpiTransaction.java              JPA entity -> upi_transactions table
├── repository/
│   ├── LedgerEntryRepository.java       findByTxnId, findAll
│   ├── TxnStatusEventRepository.java    findByTxnIdOrderByCreatedAtAsc
│   └── UpiTransactionRepository.java    findByTxnId
└── service/
    ├── DataCryptoService.java           AES-256 encrypt/decrypt for PII fields
    └── TransactionLedgerService.java    Business logic for ledger entry creation
```

---

## 5. Kafka Consumers

### 5.1 NpciResponseEventConsumer

- **Topic:** `npci.inbound.response`
- **Consumer Group:** `ledger-npci-group`
- **Purpose:** Records the raw NPCI network response as a `TxnStatusEvent` row. This captures the NPCI acknowledgment timestamp and result code for audit trail completeness.

### 5.2 SwitchCompletedEventConsumer

- **Topic:** `switch.completed`
- **Consumer Group:** `ledger-switch-group`
- **Purpose:** Receives the final saga outcome event from the Orchestrator. Triggers the main ledger entry creation via `TransactionLedgerService.createEntry()`.

---

## 6. Ledger Entry Creation

`TransactionLedgerService.createEntry()` performs the following steps:

1. Validates that the `txnId` is not already present in `ledger_entries` (idempotency check)
2. Encrypts the `payerVpa` and `payeeVpa` fields using `DataCryptoService`
3. Persists a `LedgerEntry` row with direction set to `CREDIT` (payee) or `DEBIT` (payer) based on event type
4. Persists a mirroring entry for the opposite side (double-entry bookkeeping)
5. Persists a `UpiTransaction` row capturing the overall transaction metadata and outcome

---

## 7. API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/ledger/entry/{txnId}` | Retrieve the ledger entry for a specific transaction |
| GET | `/ledger/entries` | Retrieve paginated list of all ledger entries |

### Response — LedgerEntry

| Field | Type | Description |
|---|---|---|
| `id` | Long | Unique ledger record identifier |
| `txnId` | String | PSP transaction identifier |
| `payerVpa` | String | Decrypted payer VPA |
| `payeeVpa` | String | Decrypted payee VPA |
| `amount` | BigDecimal | Transaction amount |
| `currency` | String | Currency code |
| `direction` | String | `CREDIT` or `DEBIT` |
| `status` | String | Final transaction status |
| `npciResponseCode` | String | NPCI result code |
| `createdAt` | Timestamp | Ledger record creation time |

---

## 8. PII Encryption — DataCryptoService

The `DataCryptoService` in the Ledger Service is equivalent to the implementation in the Orchestrator. It applies AES encryption to VPA fields before writing to PostgreSQL.

- **Fields encrypted:** `payerVpa`, `payeeVpa`
- **Algorithm:** AES/ECB/PKCS5Padding (demo). Production: AES/GCM/NoPadding + per-record IV
- **Safety:** Encrypt and decrypt methods return the original value on any error; they never throw exceptions
- **Key Source:** `application.properties` property `crypto.key`

---

## 9. Database Schema

### `ledger_entries` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `txn_id` | VARCHAR | Reference to the PSP transaction |
| `payer_vpa` | VARCHAR | Payer VPA (AES-256 encrypted) |
| `payee_vpa` | VARCHAR | Payee VPA (AES-256 encrypted) |
| `amount` | DECIMAL(15,2) | Transaction amount |
| `currency` | VARCHAR(3) | Currency code |
| `direction` | VARCHAR(10) | `CREDIT` or `DEBIT` |
| `status` | VARCHAR(20) | `SUCCESS`, `FAILED`, `COMPENSATED` |
| `npci_response_code` | VARCHAR | NPCI result code |
| `created_at` | TIMESTAMP | Record creation timestamp |

### `txn_status_events` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `txn_id` | VARCHAR | Transaction reference |
| `event_type` | VARCHAR | `NPCI_RESPONSE`, `SWITCH_COMPLETED` |
| `status` | VARCHAR | Status at the time of event |
| `raw_payload` | TEXT | Full JSON event payload |
| `created_at` | TIMESTAMP | Event receipt timestamp |

### `upi_transactions` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `txn_id` | VARCHAR | PSP transaction identifier |
| `txn_ref` | VARCHAR | Merchant reference |
| `amount` | DECIMAL(15,2) | Transaction amount |
| `currency` | VARCHAR(3) | Currency |
| `final_status` | VARCHAR | Final saga state |
| `npci_result` | VARCHAR | NPCI result code |
| `created_at` | TIMESTAMP | Ledger record created |
| `settled_at` | TIMESTAMP | NPCI settlement time |

---

## 10. Schema Migration — Flyway

Flyway manages schema creation and versioning. Migration scripts are located in `src/main/resources/db/migration/`. Spring Boot applies migrations automatically on startup.

---

## 11. Building the Service

```bash
cd services/psp-switch/ledger-service
mvn clean package -DskipTests
```

The service JAR is output to `target/ledger-service-1.0.0.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).

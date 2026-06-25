# PSP Ledger Service

## 1. Purpose

The PSP Ledger Service tracks PSP-level financial positions and fund movements in real-time. While the Ledger Service records individual transaction outcomes, the PSP Ledger Service aggregates those outcomes into a running position for the PSP entity itself, tracking net obligations to NPCI settlement, float balances, and debit/credit categories per settlement cycle.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `psp-ledger-service` |
| Group ID | `com.hpe` |
| Version | `0.0.1-SNAPSHOT` |
| Java Version | 21 |
| Spring Boot Version | 3.5.15 |
| Default Port | 8086 |
| Base Package | `com.hpe.psp_ledger_service` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-data-jpa | PostgreSQL persistence |
| spring-boot-starter-web | REST query endpoints |
| spring-kafka | Kafka event consumption |
| postgresql | JDBC driver |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Testing |
| spring-kafka-test | Embedded Kafka for tests |

---

## 4. Source Structure

```
com.hpe.psp_ledger_service
├── PspLedgerServiceApplication.java
├── PSPLedger.java                       JPA entity -> psp_ledger table
├── PSPLedgerConsumer.java               @KafkaListener on switch.completed
├── PSPLedgerRepository.java             Spring Data JPA repository
└── PSPLedgerService.java                Business logic for position update
```

---

## 5. Kafka Consumer — PSPLedgerConsumer

- **Topic:** `switch.completed`
- **Consumer Group:** `psp-ledger-group`
- **Behavior:** On receipt of a completed transaction event, calls `PSPLedgerService.record()` to create or update the PSP's financial position for the given settlement date.

---

## 6. PSP Ledger Logic — PSPLedgerService

For each completed transaction event:

1. Determines the entry direction based on event outcome:
   - `SUCCESS` transaction: credit entry for the payee amount due; debit entry for the PSP settlement obligation
   - `FAILED` or `COMPENSATED` transaction: records a reversal or no-change entry
2. Retrieves or creates the `PSPLedger` record for the current settlement date
3. Updates the running balance fields
4. Persists the updated record

---

## 7. Data Model — PSPLedger Entity

`PSPLedger` JPA entity mapped to `psp_ledger` table:

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `txnId` | VARCHAR | Reference transaction |
| `settlementDate` | DATE | Date of settlement cycle |
| `debitAmount` | DECIMAL(15,2) | PSP debit for this entry |
| `creditAmount` | DECIMAL(15,2) | PSP credit for this entry |
| `entryType` | VARCHAR | `PAYMENT`, `REVERSAL`, `FEE` |
| `status` | VARCHAR | Transaction final state |
| `createdAt` | TIMESTAMP | Record creation timestamp |

---

## 8. Building the Service

```bash
cd services/psp-switch/psp-ledger-service
mvn clean package -DskipTests
```

The service JAR is output to `target/psp-ledger-service-0.0.1-SNAPSHOT.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).

# Audit Service

## 1. Purpose

The Audit Service provides a compliance-grade, append-only audit trail of all significant events in the UPI transaction lifecycle. It consumes multiple Kafka topics corresponding to different stages of the payment flow and persists a structured audit log record for each event. The audit log is never modified or deleted after creation.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `audit-service` |
| Group ID | `com.audit` |
| Version | `1.0.0` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8087 |
| Base Package | `com.audit` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | HTTP endpoints (test controller) |
| spring-boot-starter-data-jpa | PostgreSQL persistence |
| postgresql | JDBC driver |
| spring-kafka | Kafka consumer |
| jackson-databind | JSON deserialization of Kafka messages |

---

## 4. Source Structure

```
com.audit
├── AuditApplication.java           Spring Boot entry point
├── AuditConsumer.java              @KafkaListener consuming multiple topics
├── AuditLog.java                   JPA entity -> audit_log table
├── AuditRepository.java            Spring Data JPA repository
├── AuditService.java               Persists AuditLog entries
└── TestController.java             GET /audit/test (verification endpoint)
```

---

## 5. Kafka Consumer — AuditConsumer

The `AuditConsumer` subscribes to five Kafka topics in a single `@KafkaListener` declaration:

| Topic | Stage | Description |
|---|---|---|
| `upi.transactions.initiated` | Initiation | Payment request accepted by Orchestrator |
| `upi.npci.verified` | NPCI Verification | NPCI confirmed or rejected the transaction |
| `upi.cbs.debit.confirm` | CBS Debit | Payer account debited by CBS |
| `upi.cbs.credit.confirm` | CBS Credit | Payee account credited by CBS |
| `upi.cbs.reversal` | Reversal | CBS compensation/reversal executed |

### Message Contract

Each Kafka message is a JSON object. The consumer deserializes it into a `Map<String, Object>` and extracts the following fields:

| JSON Field | Type | Description |
|---|---|---|
| `txnId` | String | PSP transaction identifier |
| `source` | String | Service that published the event |
| `status` | String | Status at the time of the event |
| `payer` | String | Payer VPA |
| `payee` | String | Payee VPA |
| `amount` | Number | Transaction amount |
| `stage` | String | Lifecycle stage name |
| `remarks` | String | Additional context or error reason |

---

## 6. Audit Log Entity — AuditLog

`AuditLog` is a JPA entity mapped to the `audit_log` table.

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Auto-incremented primary key |
| `txnId` | VARCHAR | Transaction identifier |
| `source` | VARCHAR | Publishing service name |
| `status` | VARCHAR | Event status |
| `payer` | VARCHAR | Payer VPA |
| `payee` | VARCHAR | Payee VPA |
| `amount` | DOUBLE | Amount (note: uses Double; see production note below) |
| `stage` | VARCHAR | Lifecycle stage |
| `remarks` | VARCHAR | Remarks or error details |
| `createdAt` | TIMESTAMP | Audit record creation timestamp (auto-set) |

**Production Note:** The `amount` field uses `Double` in the current implementation. For a production compliance system, this should be changed to `BigDecimal` mapped to `DECIMAL(15,2)` to prevent floating-point rounding errors in financial records.

---

## 7. AuditService

`AuditService.log()` accepts the individual audit fields, constructs an `AuditLog` entity, and calls `AuditRepository.save()`. The method prints a confirmation to stdout for demo visibility.

Error handling: Kafka parse errors are caught in `AuditConsumer.consume()`, logged to stdout, and do not cause consumer offset commit failure. In production, failed messages should be published to a dead-letter topic for investigation.

---

## 8. Error Handling Limitation

The current implementation swallows Kafka deserialization errors:

```java
} catch (Exception e) {
    System.out.println("Kafka parse error: " + e.getMessage());
}
```

In production, this block should publish the raw message to an `audit.dead-letter` topic and emit a structured error metric.

---

## 9. Building the Service

```bash
cd services/psp-switch/audit-service
mvn clean package -DskipTests
```

The service JAR is output to `target/audit-service-1.0.0.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).

# Bank Ledger Service

## 1. Purpose

The Bank Ledger Service maintains a bank-side audit record of all confirmed debit and credit operations processed by the Banking Switch. It listens to two Kafka confirmation topics and persists each confirmed financial movement as a ledger entry in its PostgreSQL table.

This service is independent of the Transaction Orchestrator's state machine. Its sole responsibility is to record the financial book entry after a CBS operation is confirmed as successful. The ledger entries in this service represent the bank's internal accounting view of UPI transactions.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `bank-ledger-service` |
| Group ID | `com.hpe` |
| Base Package | `com.hpe.bank_ledger_service` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8084 (Banking Switch VM) |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-data-jpa` | JPA/Hibernate for PostgreSQL |
| `spring-kafka` | Kafka consumer |
| `jackson-databind` | JSON deserialization of Kafka messages |
| `postgresql` | PostgreSQL JDBC driver |

---

## 4. Source Structure

```
com.hpe.bank_ledger_service
├── BankLedgerServiceApplication.java
├── BankLedger.java                       JPA entity mapped to table: bank_ledger
├── BankLedgerRepository.java             Spring Data JPA repository for BankLedger
├── BankLedgerService.java                Service: saves a BankLedger record
└── BankLedgerConsumer.java               @KafkaListener on debit and credit confirm topics
```

---

## 5. Processing Flow

```
Kafka topics: upi.cbs.debit.confirm  /  upi.cbs.credit.confirm
    -> BankLedgerConsumer.consume(String message)
       -> ObjectMapper.readValue(message, Map.class)
          -> Extracts: txnId, account, entryType, amount
       -> BankLedgerService.saveLedger(txnId, account, entryType, amount)
          -> Constructs BankLedger entity with status = "SUCCESS"
          -> BankLedgerRepository.save(entity)
             -> INSERT INTO bank_ledger ...
```

---

## 6. Kafka Consumer

`BankLedgerConsumer` listens on two topics simultaneously using a single `@KafkaListener`:

| Topic | Entry Type | Description |
|---|---|---|
| `upi.cbs.debit.confirm` | `DEBIT` | Triggered when a debit operation is confirmed |
| `upi.cbs.credit.confirm` | `CREDIT` | Triggered when a credit operation is confirmed |

Messages on these topics are consumed as raw JSON strings. The consumer deserializes the message using `ObjectMapper` and extracts the following fields:

| JSON Key | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `account` | String | Account number or VPA involved |
| `entryType` | String | `DEBIT` or `CREDIT` |
| `amount` | Number | Transaction amount (converted to `Double`) |

---

## 7. `BankLedgerService`

`BankLedgerService.saveLedger()` constructs a `BankLedger` entity, sets its `status` to `"SUCCESS"`, and persists it via `BankLedgerRepository`.

All entries created by this service have `status = "SUCCESS"`. Failed CBS operations do not produce a confirmation event and therefore do not result in a ledger entry.

---

## 8. Data Model — `BankLedger`

JPA entity mapped to the `bank_ledger` table.

| Field | Java Type | Column | Description |
|---|---|---|---|
| `id` | Long | `id` SERIAL PRIMARY KEY | Auto-generated entry identifier |
| `txnId` | String | `txn_id` VARCHAR | Transaction identifier |
| `account` | String | `account` VARCHAR | Account number or VPA |
| `entryType` | String | `entry_type` VARCHAR | `DEBIT` or `CREDIT` |
| `amount` | Double | `amount` DOUBLE PRECISION | Transaction amount |
| `status` | String | `status` VARCHAR | Always `SUCCESS` for entries created by this service |

---

## 9. Database

The Bank Ledger Service writes to the `bankswitch_db` PostgreSQL database on the Banking Switch VM. The `bank_ledger` table is created as part of the Banking Switch database setup.

```sql
CREATE TABLE bank_ledger (
    id          SERIAL PRIMARY KEY,
    txn_id      VARCHAR(255),
    account     VARCHAR(255),
    entry_type  VARCHAR(50),
    amount      DOUBLE PRECISION,
    status      VARCHAR(50)
);
```

---

## 10. Configuration Properties

| Property | Description |
|---|---|
| `spring.datasource.url` | JDBC URL for `bankswitch_db` on the Banking Switch VM |
| `spring.datasource.username` | PostgreSQL username |
| `spring.datasource.password` | PostgreSQL password |
| `spring.kafka.bootstrap-servers` | Kafka broker address |
| `spring.kafka.consumer.group-id` | Consumer group identifier for this service |

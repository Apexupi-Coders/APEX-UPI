# Transaction Orchestrator

## 1. Purpose

The Transaction Orchestrator is the central coordination service of the Banking Switch. It implements a Saga pattern to manage the lifecycle of every inbound UPI transaction from receipt through CBS execution to final NPCI callback dispatch. It maintains a durable state record in PostgreSQL for each transaction and produces a structured event log of every state transition.

The Orchestrator does not perform any account operations. It routes work to the CBS Adapter, receives the result, and then routes the outcome to the NPCI Response Adapter. All inter-service communication is via Kafka.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `orchestrator` |
| Group ID | `com.bankingswitch` |
| Parent Artifact | `payment-switch` |
| Base Package | `com.bankingswitch.orchestrator` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8081 (Banking Switch VM) |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST infrastructure |
| `spring-boot-starter-data-jpa` | JPA/Hibernate for PostgreSQL |
| `postgresql` | PostgreSQL JDBC driver |
| `spring-kafka` | Kafka consumer and producer |
| `jackson-dataformat-xml` | XML parsing via XmlMapper |
| `lombok` | Boilerplate reduction |
| `spring-boot-starter-test` | Unit test support |
| `spring-kafka-test` | Embedded Kafka for integration tests |

---

## 4. Source Structure

```
com.bankingswitch.orchestrator
├── OrchestratorApplication.java
├── consumer/
│   ├── InboundEventConsumer.java         @KafkaListener on banking.inbound.txn
│   └── CbsResponseConsumer.java          @KafkaListener on banking.cbs.response
├── model/
│   ├── TransactionState.java             Enum: RECEIVED, CBS_PENDING, CBS_SUCCESS, CBS_FAILED, SUCCESS, FAILED
│   ├── InboundTransactionEvent.java      Consumed from banking.inbound.txn
│   ├── CbsRequestEvent.java              Produced to banking.cbs.request
│   ├── CbsResponseEvent.java             Consumed from banking.cbs.response
│   ├── NpciResponseEvent.java            Produced to banking.npci.response
│   └── entity/
│       ├── TransactionEntity.java        JPA entity mapped to table: transactions
│       └── EventLogEntity.java           JPA entity mapped to table: event_log
├── producer/
│   ├── CbsRequestProducer.java           Publishes CbsRequestEvent to banking.cbs.request
│   └── NpciResponseProducer.java         Publishes NpciResponseEvent to banking.npci.response
├── repository/
│   ├── TransactionRepository.java        Spring Data JPA for TransactionEntity
│   └── EventLogRepository.java           Spring Data JPA for EventLogEntity
└── service/
    ├── SagaOrchestratorService.java      Core saga logic: inbound routing and CBS response handling
    └── TransactionStateService.java      Creates transactions and records state transitions
```

---

## 5. Saga Flow — Inbound Processing

When an `InboundTransactionEvent` is consumed, `SagaOrchestratorService.processInboundEvent()` executes the following logic:

### Step 1 — Create Transaction Record

`TransactionStateService.createTransaction()` is called with the `txnId`, `txnType`, and raw `xmlPayload`. A `TransactionEntity` is persisted to the `transactions` table in state `RECEIVED`. A corresponding entry is appended to `event_log`.

### Step 2 — Parse XML and Determine Operation

The XML payload is parsed using Jackson's `XmlMapper` to extract the relevant VPA and amount fields. The operation type is determined by `txnType`:

| `txnType` | CBS Operation | Data Extracted From |
|---|---|---|
| `ReqBalEnq` | `BALANCE` | `Txn.Payer.addr` (VPA) |
| `ReqPay` | `DEBIT` | `Txn.Payer.addr` (VPA), `Txn.Payer.Amount.value` (amount) |
| `ReqCredit` | `CREDIT` | `Txn.Payee.addr` (VPA), `Txn.Payee.Amount.value` (amount) |

### Step 3 — Transition to CBS_PENDING and Publish

The transaction state is updated to `CBS_PENDING`. A `CbsRequestEvent` is constructed and published to the `banking.cbs.request` Kafka topic.

### Step 4 — XML Parse Error Handling

If XML parsing fails at any point, the transaction is immediately transitioned to state `FAILED` with the reason `"XML Parse Error"` and no CBS request is issued.

---

## 6. Saga Flow — CBS Response Processing

When a `CbsResponseEvent` is consumed, `SagaOrchestratorService.processCbsResponse()` executes:

### Step 1 — Retrieve Transaction

The transaction is retrieved from PostgreSQL by `txnId`. If the record does not exist, the event is discarded.

### Step 2 — Transition Based on CBS Status

| CBS `status` | Intermediate State | Final State |
|---|---|---|
| `SUCCESS` | `CBS_SUCCESS` | `SUCCESS` |
| Any other value | `CBS_FAILED` | `FAILED` |

Both the intermediate and final state transitions are persisted to `transactions` and logged to `event_log`.

### Step 3 — Determine Response Type

The UPI response message type is determined from the original `txnType`:

| Original `txnType` | NPCI Response Type |
|---|---|
| `ReqBalEnq` | `RespBalEnq` |
| `ReqCredit` | `RespCredit` |
| `ReqPay` | `RespPay` |

### Step 4 — Publish NPCI Response Event

A `NpciResponseEvent` is constructed with the resolved `txnType`, `status`, `errorCode`, and `balance` fields, and published to the `banking.npci.response` Kafka topic for pickup by the NPCI Response Adapter.

---

## 7. Transaction State Machine

```
[RECEIVED]
    |
    | (XML parsed successfully)
    v
[CBS_PENDING]
    |
    |-- (CBS status = SUCCESS) --> [CBS_SUCCESS] --> [SUCCESS]
    |
    |-- (CBS status != SUCCESS) --> [CBS_FAILED]  --> [FAILED]
    |
    | (XML parse error)
    v
[FAILED]
```

---

## 8. Kafka Topics

| Topic | Role | Event Type |
|---|---|---|
| `banking.inbound.txn` | Consumed | `InboundTransactionEvent` |
| `banking.cbs.request` | Produced | `CbsRequestEvent` |
| `banking.cbs.response` | Consumed | `CbsResponseEvent` |
| `banking.npci.response` | Produced | `NpciResponseEvent` |

### `CbsRequestEvent` Schema

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `operation` | String | `BALANCE`, `DEBIT`, or `CREDIT` |
| `vpa` | String | VPA of the account to operate on |
| `amount` | Double | Transaction amount (null for BALANCE) |
| `xmlPayload` | String | Original raw XML, passed through for later use |

### `NpciResponseEvent` Schema

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `txnType` | String | `RespBalEnq`, `RespPay`, or `RespCredit` |
| `status` | String | `SUCCESS` or failure code |
| `errorCode` | String | CBS error code if operation failed |
| `balance` | Double | Account balance (for balance enquiries only) |
| `xmlPayload` | String | Original inbound XML payload |

---

## 9. Database Schema

The Orchestrator owns the `bankswitch_db` database on the Banking Switch VM.

### `transactions` Table

| Column | Type | Description |
|---|---|---|
| `txn_id` | VARCHAR(255) PRIMARY KEY | Transaction identifier |
| `txn_type` | VARCHAR(50) | `ReqBalEnq`, `ReqPay`, or `ReqCredit` |
| `state` | VARCHAR(50) | Current state from `TransactionState` enum |
| `payer_vpa` | VARCHAR(255) | Payer VPA (populated from XML if available) |
| `payee_vpa` | VARCHAR(255) | Payee VPA (populated from XML if available) |
| `amount` | DOUBLE PRECISION | Transaction amount |
| `xml_payload` | TEXT | Full raw XML payload |
| `created_at` | BIGINT | Unix epoch milliseconds |
| `updated_at` | BIGINT | Unix epoch milliseconds of last state change |

### `event_log` Table

| Column | Type | Description |
|---|---|---|
| `id` | SERIAL PRIMARY KEY | Auto-generated log entry identifier |
| `txn_id` | VARCHAR(255) | Foreign reference to `transactions.txn_id` |
| `event_type` | VARCHAR(50) | State name or `CREATED` |
| `event_data` | TEXT | Human-readable description of the state transition |
| `timestamp` | BIGINT | Unix epoch milliseconds |

---

## 10. TransactionStateService

`TransactionStateService` is the only component that writes to `transactions` and `event_log`. All writes are `@Transactional`.

| Method | Description |
|---|---|
| `createTransaction(txnId, txnType, xmlPayload)` | Inserts a new row in `transactions` with state `RECEIVED`. Appends a `CREATED` event to `event_log`. Returns the created `TransactionEntity`. |
| `updateTransactionState(txnId, state, eventData)` | Updates `state` and `updatedAt` on the `transactions` row. Appends a new event to `event_log`. |
| `getTransaction(txnId)` | Retrieves the `TransactionEntity` by primary key. Returns `null` if not found. |

---

## 11. Configuration Properties

| Property | Description |
|---|---|
| `spring.datasource.url` | JDBC URL for `bankswitch_db` on the Banking Switch VM (format: `jdbc:postgresql://<host>:<port>/bankswitch_db`) |
| `spring.datasource.username` | PostgreSQL username |
| `spring.datasource.password` | PostgreSQL password |
| `spring.kafka.bootstrap-servers` | Kafka broker address |
| `kafka.topic.inbound-txn` | Inbound Kafka topic name |
| `kafka.topic.cbs-request` | CBS request Kafka topic name |
| `kafka.topic.cbs-response` | CBS response Kafka topic name |
| `kafka.topic.npci-response` | NPCI response Kafka topic name |

# CBS Adapter

## 1. Purpose

The CBS Adapter is the bridge between the Kafka-based internal messaging fabric of the Banking Switch and the REST API of the Core Banking System (CBS). It consumes CBS operation request events from Kafka, translates each request into the appropriate HTTP call against the CBS service, and publishes the CBS result back onto a Kafka response topic for the Transaction Orchestrator to consume.

The CBS Adapter does not perform any account operations itself. It only translates and dispatches. All business logic — balance checks, debit execution, credit execution — is owned exclusively by the CBS service.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `cbs-adapter` |
| Group ID | `com.bankingswitch` |
| Parent Artifact | `payment-switch` |
| Base Package | `com.bankingswitch.cbsadapter` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8082 (Banking Switch VM) |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | RestTemplate HTTP client infrastructure |
| `spring-kafka` | Kafka consumer and producer |
| `lombok` | Boilerplate reduction |
| `spring-boot-starter-test` | Unit test support |

---

## 4. Source Structure

```
com.bankingswitch.cbsadapter
├── CbsAdapterApplication.java
├── config/
│   └── RestClientConfig.java             Spring bean definition for RestTemplate
├── consumer/
│   └── CbsRequestConsumer.java           @KafkaListener on banking.cbs.request
├── model/
│   ├── CbsRequestEvent.java              Consumed event: operation, VPA, amount, txnId
│   ├── CbsResponseEvent.java             Produced event: status, errorCode, balance
│   ├── CbsOperationRequest.java          HTTP request body for debit and credit calls
│   └── CbsApiResponse.java               HTTP response body from CBS REST API
├── producer/
│   └── CbsResponseProducer.java          Publishes CbsResponseEvent to banking.cbs.response
└── service/
    └── CbsClientService.java             HTTP client: getBalance, processDebit, processCredit
```

---

## 5. Processing Flow

```
Kafka topic: banking.cbs.request
    -> CbsRequestConsumer.consume(CbsRequestEvent)
       -> Determine operation from event.getOperation()
          |
          |-- "BALANCE" -> CbsClientService.getBalance(vpa)
          |                  -> HTTP GET /cbs/balance/{vpa}
          |
          |-- "DEBIT"   -> CbsClientService.processDebit(txnId, vpa, amount)
          |                  -> HTTP POST /cbs/debit
          |
          |-- "CREDIT"  -> CbsClientService.processCredit(txnId, vpa, amount)
          |                  -> HTTP POST /cbs/credit
          |
          |-- unknown   -> Synthetic FAILED response (errorCode: INVALID_OP)
          |
       -> Build CbsResponseEvent {txnId, operation, status, errorCode, balance, xmlPayload}
       -> CbsResponseProducer.sendResponse(event)
          -> Kafka topic: banking.cbs.response
```

---

## 6. CBS HTTP Client — `CbsClientService`

`CbsClientService` makes HTTP calls to the CBS service. The CBS host address is injected from configuration; no addresses are hardcoded.

### 6.1 getBalance

| Attribute | Value |
|---|---|
| Method | GET |
| Path | `/cbs/balance/{vpa}` |
| Path Variable | `vpa` — the account VPA to query |
| Returns | `CbsApiResponse` containing `status` and `balance` |

On success, CBS returns `status = "SUCCESS"` and the `balance` field populated.

On failure (CBS unavailable or account not found), the method returns a synthetic `CbsApiResponse` with `status = "FAILED"` and `errorCode = "CBS_UNAVAILABLE"`.

### 6.2 processDebit

| Attribute | Value |
|---|---|
| Method | POST |
| Path | `/cbs/debit` |
| Request Body | `CbsOperationRequest` — `{txnId, vpa, amount}` |
| Returns | `CbsApiResponse` containing `status` and balances |

On success, CBS returns `status = "SUCCESS"`.
On insufficient funds, CBS returns `status = "INSUFFICIENT_FUNDS"`.
On CBS unavailability or exception, the method returns `status = "FAILED"` and `errorCode = "CBS_UNAVAILABLE"`.

### 6.3 processCredit

| Attribute | Value |
|---|---|
| Method | POST |
| Path | `/cbs/credit` |
| Request Body | `CbsOperationRequest` — `{txnId, vpa, amount}` |
| Returns | `CbsApiResponse` containing `status` and balances |

On success, CBS returns `status = "SUCCESS"`.
On CBS unavailability or exception, the method returns `status = "FAILED"` and `errorCode = "CBS_UNAVAILABLE"`.

---

## 7. Error Handling

All three methods in `CbsClientService` wrap their HTTP calls in try/catch blocks. If `RestTemplate` throws any exception (connection refused, timeout, HTTP error), the method catches the exception, logs the error, and returns a constructed failure `CbsApiResponse` rather than propagating the exception. This ensures the Kafka consumer always produces a response event and does not enter an error loop.

| Failure Scenario | Resulting `status` | Resulting `errorCode` |
|---|---|---|
| CBS service unreachable | `FAILED` | `CBS_UNAVAILABLE` |
| Unknown operation in event | `FAILED` | `INVALID_OP` |
| Insufficient funds | `INSUFFICIENT_FUNDS` | (set by CBS) |
| Account not found | Varies | (set by CBS) |

---

## 8. Kafka Events

### Consumed — `CbsRequestEvent` (from `banking.cbs.request`)

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `operation` | String | `BALANCE`, `DEBIT`, or `CREDIT` |
| `vpa` | String | VPA of the account to operate on |
| `amount` | Double | Amount for DEBIT or CREDIT (null for BALANCE) |
| `xmlPayload` | String | Original UPI XML, passed through unchanged |

### Produced — `CbsResponseEvent` (to `banking.cbs.response`)

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `operation` | String | Echo of the requested operation |
| `status` | String | `SUCCESS`, `FAILED`, `INSUFFICIENT_FUNDS`, or `CBS_UNAVAILABLE` |
| `errorCode` | String | Specific error code if status is not SUCCESS |
| `balance` | Double | Account balance (populated for BALANCE operations) |
| `xmlPayload` | String | Original UPI XML, passed through unchanged |

---

## 9. HTTP Request Models

### `CbsOperationRequest`

Used as the JSON body for POST /cbs/debit and POST /cbs/credit:

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier for idempotency |
| `vpa` | String | VPA of the account |
| `amount` | Double | Amount to debit or credit |

### `CbsApiResponse`

The response JSON body received from CBS:

| Field | Type | Description |
|---|---|---|
| `status` | String | Operation result: `SUCCESS`, `FAILED`, `INSUFFICIENT_FUNDS` |
| `errorCode` | String | Error code if applicable |
| `balance` | Double | Current balance after operation (or enquired balance) |

---

## 10. Configuration Properties

| Property | Description |
|---|---|
| `cbs.host` | Base URL of the CBS service (format: `http://<CBS-VM-IP>:<port>`) |
| `spring.kafka.bootstrap-servers` | Kafka broker address |
| `kafka.topic.cbs-request` | Kafka topic to consume from |
| `kafka.topic.cbs-response` | Kafka topic to publish results to |

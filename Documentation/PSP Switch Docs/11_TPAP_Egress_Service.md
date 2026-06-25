# TPAP Egress Service

## 1. Purpose

The TPAP Egress Service is the outbound notification gateway of the PSP Switch. After a transaction reaches its final state, the Transaction Orchestrator publishes a `switch.completed` event to Kafka. The Egress Service consumes this event, identifies the correct payload shape based on the event type, looks up the registered webhook URL for the originating TPAP, and delivers the result via an outbound HTTP POST. It implements structured retry logic and records every delivery attempt in a persistent delivery log.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `tpap-egress-service` |
| Group ID | `com.pspswitch` |
| Version | `1.0.0-SNAPSHOT` |
| Java Version | 21 |
| Spring Boot Version | 3.3.0 |
| Default Port | 8085 |
| Base Package | `com.pspswitch.tpapegress` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | Health endpoint |
| spring-boot-starter-webflux | WebClient for outbound HTTP posts |
| spring-boot-starter-data-jpa | PostgreSQL persistence |
| postgresql | JDBC driver |
| spring-kafka | Kafka consumer |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Testing |
| spring-kafka-test | Embedded Kafka for tests |
| h2 | In-memory DB for tests |

---

## 4. Source Structure

```
com.pspswitch.tpapegress
├── TpapEgressApplication.java
├── client/
│   └── WebhookHttpClient.java              WebClient-based HTTP POST wrapper
├── consumer/
│   └── SwitchCompletedEventConsumer.java   @KafkaListener on switch.completed
├── dispatcher/
│   ├── WebhookDispatcherService.java       Routes event to handler via factory
│   ├── EventHandlerFactory.java            Resolves WebhookEventHandler by EventType
│   └── handler/
│       ├── WebhookEventHandler.java        Interface: supportedType() + handle()
│       ├── PaymentPushHandler.java         Handles PAYMENT_PUSH events
│       ├── BalanceInquiryHandler.java      Handles BALANCE_INQUIRY events
│       └── VpaVerificationHandler.java     Handles VPA_VERIFICATION events
├── exception/
│   ├── UnknownEventTypeException.java      Thrown for unregistered event types
│   ├── WebhookConfigNotFoundException.java  Thrown when no webhook URL is configured
│   └── WebhookDeliveryException.java       Thrown on network-level delivery failure
├── model/
│   ├── entity/
│   │   ├── WebhookConfig.java              JPA: webhook_configs table
│   │   └── DeliveryLog.java                JPA: delivery_logs table
│   ├── event/
│   │   ├── SwitchCompletedEvent.java       Kafka input event envelope
│   │   ├── EventType.java                  Enum: PAYMENT_PUSH, BALANCE_INQUIRY, VPA_VERIFICATION
│   │   ├── PaymentPushEvent.java           Concrete event for payment results
│   │   ├── BalanceInquiryEvent.java        Concrete event for balance results
│   │   └── VpaVerificationEvent.java       Concrete event for VPA verification results
│   └── payload/
│       ├── WebhookPayload.java             Common interface for all webhook payloads
│       ├── PaymentPushWebhookPayload.java   Payment result delivered to TPAP
│       ├── BalanceInquiryWebhookPayload.java Balance result delivered to TPAP
│       └── VpaVerificationWebhookPayload.java VPA verification result delivered to TPAP
└── repository/
    ├── DeliveryLogRepository.java          save(DeliveryLog)
    └── WebhookConfigRepository.java        findByTpapIdAndEventType(...)
```

---

## 5. Event Dispatch Flow

```
Kafka: switch.completed
  -> SwitchCompletedEventConsumer.consume(SwitchCompletedEvent)
     -> WebhookDispatcherService.dispatch(event)
        -> EventHandlerFactory.getHandler(event.getEventType())
           -> (resolves to PaymentPushHandler | BalanceInquiryHandler | VpaVerificationHandler)
              -> handler.handle(event)
                 -> WebhookConfigRepository.findActiveConfig(tpapId, eventType)
                    -> (if not found: skip, log SKIPPED, return)
                 -> Builds typed WebhookPayload
                 -> WebhookHttpClient.post(url, payload)  [single HTTP call]
                    -> On 2xx: save DeliveryLog(status=SUCCESS)
                    -> On 5xx or timeout: retry up to 3 times
                    -> On 4xx: no retry; save DeliveryLog(status=FAILED)
                    -> After 3 failed retries: save DeliveryLog(status=FAILED)
```

---

## 6. Architectural Decision Records (ADRs)

### ADR-001 — Polymorphic Handler Pattern

The `EventHandlerFactory` resolves the correct `WebhookEventHandler` implementation by matching `EventType`. This eliminates all conditional dispatch (if-else or switch statements) from the dispatcher. Adding support for a new event type requires only creating a new `@Component` that implements `WebhookEventHandler` and returns the new `EventType` from `supportedType()`.

### ADR-002 — Retry Semantics

- Retry up to 3 times on HTTP 5xx responses or connection timeouts.
- Do not retry on HTTP 4xx responses. A 4xx indicates a client-side misconfiguration (wrong URL, incorrect payload schema) that retrying will not resolve.
- All retry logic is implemented within the handler, not in `WebhookHttpClient`. The client makes exactly one HTTP call per invocation.

### ADR-003 — Silent Skip on Missing Config

If no active `WebhookConfig` entry exists for a `(tpapId, eventType)` pair, the handler returns silently without throwing an exception. A `DeliveryLog` entry with `status=SKIPPED` is persisted. This prevents blocking the Kafka consumer offset commit due to a configuration gap.

### ADR-005 — Single-Call HTTP Client

`WebhookHttpClient.post()` makes exactly one HTTP call with no internal retry. This keeps the client simple and testable. All retry orchestration is the responsibility of the calling handler.

---

## 7. Kafka Consumer — SwitchCompletedEventConsumer

- **Topic:** `switch.completed`
- **Consumer Group:** `tpap-egress-group`
- **Behavior:** Deserializes the `SwitchCompletedEvent` JSON envelope and calls `WebhookDispatcherService.dispatch()`. Exceptions propagate to the Kafka container's error handler, which commits the offset by default after maximum retry exhaustion.

---

## 8. HTTP Client — WebhookHttpClient

`WebhookHttpClient` wraps Spring WebFlux `WebClient` for outbound HTTP.

- **Method:** `post(String url, WebhookPayload payload)`
- **Timeout:** Configurable; default 5 seconds
- **Serialization:** Jackson JSON
- **Returns:** HTTP status code as integer
- **Throws:** `WebhookDeliveryException` on network-level failure (connection refused, DNS failure, read timeout)

---

## 9. Data Models

### WebhookConfig — `webhook_configs` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `tpap_id` | VARCHAR | Registered TPAP identifier |
| `event_type` | VARCHAR | `PAYMENT_PUSH`, `BALANCE_INQUIRY`, or `VPA_VERIFICATION` |
| `webhook_url` | VARCHAR | Target HTTPS endpoint for webhook delivery |
| `active` | BOOLEAN | If false, delivery is skipped (ADR-003) |
| `created_at` | TIMESTAMP | Configuration registration timestamp |
| `updated_at` | TIMESTAMP | Last modification timestamp |

### DeliveryLog — `delivery_logs` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `txn_id` | VARCHAR | Transaction identifier |
| `tpap_id` | VARCHAR | Target TPAP |
| `event_type` | VARCHAR | Event type dispatched |
| `webhook_url` | VARCHAR | URL attempted |
| `status` | VARCHAR | `SUCCESS`, `FAILED`, or `SKIPPED` |
| `http_status` | INTEGER | HTTP response status code (null if network failure) |
| `attempt_count` | INTEGER | Number of delivery attempts made |
| `delivered_at` | TIMESTAMP | Timestamp of first successful delivery |
| `created_at` | TIMESTAMP | Log record creation timestamp |

---

## 10. Webhook Payload Schemas

### PaymentPushWebhookPayload

| Field | Type | Description |
|---|---|---|
| `eventId` | String | Unique event identifier |
| `eventType` | String | `PAYMENT_PUSH` |
| `tpapId` | String | Target TPAP |
| `txnId` | String | PSP transaction identifier |
| `correlationId` | String | Maps to TPAP's original `txnRef` |
| `deliveredAt` | String | ISO-8601 delivery timestamp |
| `status` | String | `SUCCESS`, `FAILED`, or `COMPENSATED` |
| `amount` | BigDecimal | Transaction amount |
| `currency` | String | Currency code |
| `payerVpa` | String | Payer UPI VPA |
| `payeeVpa` | String | Payee UPI VPA |
| `failureReason` | String | Reason for failure (null on SUCCESS) |

### BalanceInquiryWebhookPayload

| Field | Type | Description |
|---|---|---|
| `eventId` | String | Unique event identifier |
| `eventType` | String | `BALANCE_INQUIRY` |
| `tpapId` | String | Target TPAP |
| `txnId` | String | PSP transaction identifier |
| `correlationId` | String | Original request correlation |
| `deliveredAt` | String | ISO-8601 delivery timestamp |
| `vpa` | String | Queried VPA |
| `balance` | BigDecimal | Account balance |
| `currency` | String | Currency code |

### VpaVerificationWebhookPayload

| Field | Type | Description |
|---|---|---|
| `eventId` | String | Unique event identifier |
| `eventType` | String | `VPA_VERIFICATION` |
| `tpapId` | String | Target TPAP |
| `txnId` | String | PSP transaction identifier |
| `correlationId` | String | Original request correlation |
| `deliveredAt` | String | ISO-8601 delivery timestamp |
| `vpa` | String | Verified VPA |
| `verified` | Boolean | Whether the VPA is registered and active |
| `accountHolderName` | String | Registered name for the VPA |

---

## 11. Building the Service

```bash
cd services/psp-switch/tpap-egress-service
mvn clean package -DskipTests
```

The service JAR is output to `target/tpap-egress-service-1.0.0-SNAPSHOT.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).

# TPAP Ingress Service

## 1. Purpose

The TPAP Ingress Service is the authenticated HTTP entry point to the APEX-UPI PSP Switch. It accepts payment initiation, balance inquiry, and VPA lookup requests from registered Third-Party Application Providers (TPAPs). Its primary responsibilities are authentication, idempotency enforcement, request normalization, and Kafka publication. The service does not process transactions itself; it delegates all business logic downstream via Kafka.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `tpap-ingress-service` |
| Group ID | `com.pspswitch` |
| Version | `1.0.0-SNAPSHOT` |
| Java Version | 17 |
| Spring Boot Version | 3.3.0 |
| Default Port | 8080 |
| Base Package | `com.pspswitch.tpapingress` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | REST endpoint serving |
| spring-boot-starter-data-jpa | PostgreSQL persistence for idempotency records |
| postgresql | JDBC driver |
| spring-boot-starter-data-redis | Redis for idempotency key storage |
| spring-kafka | Kafka publishing |
| springdoc-openapi-starter-webmvc-ui 2.5.0 | OpenAPI/Swagger documentation |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Unit and integration testing |
| spring-kafka-test | Embedded Kafka for tests |
| h2 | In-memory DB for tests |

---

## 4. Source Structure

```
com.pspswitch.tpapingress
├── TpapIngressApplication.java         Spring Boot entry point
├── config/
│   ├── JacksonConfig.java              Jackson ObjectMapper customization
│   ├── KafkaConfig.java                KafkaTemplate and ProducerFactory configuration
│   ├── OpenApiConfig.java              Swagger UI title, description, version
│   └── RedisConfig.java                RedisTemplate and connection factory setup
├── controller/
│   ├── PaymentController.java          POST /payment/initiate
│   ├── BalanceInquiryController.java   POST /balance/inquiry
│   ├── VpaLookupController.java        POST /vpa/lookup
│   ├── StatusController.java           GET /status/{txnRef}
│   └── HealthController.java           GET /health
├── dto/
│   ├── request/
│   │   ├── PaymentInitiateRequest.java     Inbound payment DTO
│   │   ├── BalanceInquiryRequest.java      Inbound balance DTO
│   │   └── VpaLookupRequest.java           Inbound VPA lookup DTO
│   └── response/
│       ├── AcceptedResponse.java           HTTP 202 response body
│       ├── TransactionStatus.java          GET /status response DTO
│       ├── HealthResponse.java             Health check response
│       ├── ErrorResponse.java              Standard error envelope
│       └── ErrorDetail.java                Individual error field
├── exception/
│   ├── GlobalExceptionHandler.java         @ControllerAdvice for all exceptions
│   ├── RequestValidationException.java     Thrown on invalid request fields
│   ├── IdempotencyStoreException.java      Thrown on Redis write failure
│   ├── KafkaPublishFailureException.java   Thrown on Kafka send failure
│   ├── KafkaUnavailableException.java      Thrown on Kafka broker unavailability
│   └── RateLimitExceededException.java     Thrown on TPAP rate limit breach
├── filter/
│   └── TpapAuthFilter.java                 Servlet filter for bearer token authentication
├── idempotency/
│   ├── IdempotencyRecord.java              JPA entity persisted to idempotency_records table
│   └── IdempotencyRepository.java          Spring Data JPA repository
├── kafka/
│   └── KafkaEventEnvelope.java             Kafka message wrapper (eventType, payload, metadata)
└── service/
    ├── IdempotencyService.java             Redis SETNX-based idempotency enforcement
    ├── KafkaPublisherService.java          Publishes normalized events to Kafka topics
    └── TpapAuthService.java                Token validation against registered TPAP credentials
```

---

## 5. API Endpoints

### 5.1 Payment Initiation

| Attribute | Value |
|---|---|
| Method | POST |
| Path | `/payment/initiate` |
| Authentication | Bearer token (TpapAuthFilter) |
| Content-Type | `application/json` |
| Idempotency | Redis SETNX on `(txnRef + payeeVpa)` composite key |
| Success Response | HTTP 202 with `AcceptedResponse` body |
| Duplicate Response | HTTP 202 with previously stored `AcceptedResponse` |

Request body (`PaymentInitiateRequest`):

| Field | Type | Required | Description |
|---|---|---|---|
| `txnRef` | String | Yes | Merchant/TPAP transaction reference |
| `payerVpa` | String | Yes | Payer UPI VPA (e.g., `user@demopsp`) |
| `payeeVpa` | String | Yes | Payee UPI VPA (e.g., `merchant@okaxis`) |
| `amount` | BigDecimal | Yes | Transaction amount in INR |
| `currency` | String | Yes | Must be `INR` |
| `remarks` | String | No | Optional payment remarks |
| `mcc` | String | No | Merchant Category Code |

Response body (`AcceptedResponse`):

| Field | Type | Description |
|---|---|---|
| `txnId` | String | PSP-assigned internal transaction identifier |
| `txnRef` | String | Echo of the submitted reference |
| `status` | String | Always `ACCEPTED` at this stage |
| `timestamp` | String | ISO-8601 acceptance timestamp |

### 5.2 Balance Inquiry

| Attribute | Value |
|---|---|
| Method | POST |
| Path | `/balance/inquiry` |
| Authentication | Bearer token |
| Content-Type | `application/json` |

Request body (`BalanceInquiryRequest`):

| Field | Type | Required | Description |
|---|---|---|---|
| `vpa` | String | Yes | VPA of the account to query |
| `tpapId` | String | Yes | Registered TPAP identifier |

### 5.3 VPA Lookup

| Attribute | Value |
|---|---|
| Method | POST |
| Path | `/vpa/lookup` |
| Authentication | Bearer token |
| Content-Type | `application/json` |

### 5.4 Transaction Status

| Attribute | Value |
|---|---|
| Method | GET |
| Path | `/status/{txnRef}` |
| Authentication | Bearer token |
| Success Response | `TransactionStatus` with current state |

### 5.5 Health

| Attribute | Value |
|---|---|
| Method | GET |
| Path | `/health` |
| Authentication | None |
| Success Response | `HealthResponse` with service and dependency status |

---

## 6. Authentication — TpapAuthFilter

The `TpapAuthFilter` is a `OncePerRequestFilter` registered in the Spring Security filter chain. It intercepts all requests except the health endpoint.

**Token Extraction:** The filter reads the `Authorization` header. Expected format: `Bearer <token>`.

**Validation Mechanism:** The token is passed to `TpapAuthService`, which validates it against a registry of known TPAP identifiers and shared secrets. In the reference implementation this is an in-memory map; production would use a secrets store or OAuth introspection endpoint.

**Failure Behavior:** An invalid or missing token results in HTTP 401 with a structured `ErrorResponse`. The request does not reach any controller.

---

## 7. Idempotency — IdempotencyService

The idempotency mechanism prevents duplicate transaction processing in the event of TPAP retry.

**Key:** `idempotency::{txnRef}::{payeeVpa}` stored in Redis.

**Mechanism:**
1. Before publishing to Kafka, `IdempotencyService` attempts a Redis `SETIFABSENT` (SETNX equivalent) on the composite key.
2. If the key did not exist, it is created with a configurable TTL (default 1 hour) and the request proceeds to Kafka publication.
3. If the key already exists, the service retrieves the previously stored `AcceptedResponse` from Redis and returns it immediately without republishing.
4. An `IdempotencyRecord` entity is also persisted to PostgreSQL to ensure durability across Redis restarts.

**Failure Handling:** If the Redis write fails, `IdempotencyStoreException` is thrown and the global exception handler returns HTTP 503.

---

## 8. Kafka Publication — KafkaPublisherService

`KafkaPublisherService` wraps the `KafkaTemplate` and serializes requests into `KafkaEventEnvelope` objects before publication.

**Envelope structure:**

| Field | Description |
|---|---|
| `eventId` | UUID v4 generated per event |
| `eventType` | `PAYMENT_INITIATE`, `BALANCE_INQUIRY`, or `VPA_LOOKUP` |
| `tpapId` | Originating TPAP identifier |
| `correlationId` | Maps back to the `txnRef` for end-to-end tracing |
| `payload` | Serialized request DTO |
| `publishedAt` | ISO-8601 timestamp |

**Topic:** `upi.txn.requests`

**Error Handling:** On Kafka send failure, `KafkaPublishFailureException` is thrown. The global exception handler returns HTTP 503 to the TPAP, enabling the TPAP to retry with the same idempotency key.

---

## 9. Error Handling

`GlobalExceptionHandler` (@ControllerAdvice) handles all exceptions and maps them to structured `ErrorResponse` objects.

| Exception | HTTP Status | Description |
|---|---|---|
| `RequestValidationException` | 400 | Request field validation failure |
| `RateLimitExceededException` | 429 | TPAP rate limit exceeded |
| `IdempotencyStoreException` | 503 | Redis unavailable |
| `KafkaUnavailableException` | 503 | Kafka broker unavailable |
| `KafkaPublishFailureException` | 503 | Kafka publish timed out or failed |
| Any other `RuntimeException` | 500 | Unexpected internal error |

---

## 10. Building the Service

```bash
cd services/psp-switch/tpap-ingress-service
mvn clean package -DskipTests
```

The service JAR is output to `target/tpap-ingress-service-1.0.0-SNAPSHOT.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).

OpenAPI documentation is available at the `/swagger-ui.html` path once the service is running.

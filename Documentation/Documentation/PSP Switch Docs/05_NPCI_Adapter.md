# NPCI Adapter Service

## 1. Purpose

The NPCI Adapter is the sole service within the PSP Switch that communicates directly with the National Payments Corporation of India (NPCI) external network. It enforces a strict separation between the PSP Switch's internal Kafka-based communication fabric and the NPCI-prescribed HTTP REST + XML protocol. No other PSP Switch service contacts NPCI directly, and NPCI never interacts with Kafka.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `npci-adapter` |
| Group ID | `com.psp.npci` |
| Version | `1.0.0` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8081 (development) / 8082 (Docker Compose cloud) |
| Base Package | `com.psp.npci.adapter` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | REST endpoints for NPCI callbacks |
| spring-kafka | Kafka consumer and producer |
| spring-boot-starter-actuator | Health checks |
| commons-codec | SHA-256 hex encoding for request signing |
| httpclient5 | Apache HttpClient 5 for future mTLS SSLContext support |
| jackson-databind | JSON serialization of Kafka payloads |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Testing |
| spring-kafka-test | Embedded Kafka for tests |

---

## 4. Source Structure

```
com.psp.npci.adapter
├── NpciAdapterApplication.java
├── config/
│   ├── KafkaConfig.java                Consumer and producer factory configuration
│   ├── RestTemplateConfig.java         mTLS-aware RestTemplate (SSLContext configurable)
│   └── AsyncConfig.java                @EnableAsync thread pool
├── model/
│   ├── NpciOutboundRequestEvent.java   Kafka consumed event (PSP -> NPCI)
│   └── NpciInboundResponseEvent.java   Kafka produced event (NPCI -> PSP)
├── consumer/
│   └── NpciOutboundConsumer.java       @KafkaListener on npci.outbound.request
├── controller/
│   └── NpciWebhookController.java      POST /upi/RespPay/..., POST /upi/ReqPay/...
├── producer/
│   └── NpciResponseProducer.java       Publishes to npci.inbound.response
└── service/
    ├── NpciAdapterService.java         Orchestrates XML build, sign, HTTP dispatch
    ├── XmlBuilderService.java          Constructs UPI XML messages
    ├── XmlParserService.java           Parses NPCI XML responses
    ├── SigningService.java             SHA-256 signing (production: ECDSA P-256)
    ├── EncryptionService.java          MPIN encryption (production: AES-256-GCM)
    └── IdempotencyService.java         In-memory ConcurrentHashMap duplicate guard
```

---

## 5. Transaction Flows

### Flow A — PAY (Asynchronous Callback)

This is the primary flow for peer-to-peer UPI payments.

```
Transaction Orchestrator
  -> Kafka topic: npci.outbound.request (type=PAY)
     -> NpciOutboundConsumer.consume()
        -> NpciAdapterService.processPay()
           -> XmlBuilderService.buildReqPay()
           -> SigningService.sign()
           -> HTTP POST to NPCI /upi/ReqPay endpoint
              -> NPCI returns ReqPay Ack (not the final result)
                 -> ~2 seconds later NPCI fires RespPay callback
                    -> POST /upi/RespPay/1.0/urn:txnid/{txnId}
                       -> NpciWebhookController.handleRespPay()
                          -> HTTP 200 Ack returned to NPCI immediately
                          -> @Async: XmlParserService.parseRespPay()
                             -> NpciResponseProducer.publish()
                                -> Kafka: npci.inbound.response (result=SUCCESS|FAILURE|TIMEOUT)
                                   -> Transaction Orchestrator resolves saga
```

### Flow B — BALANCE ENQUIRY (Synchronous)

Used for account balance checks. NPCI responds in the same HTTP call.

```
Transaction Orchestrator
  -> Kafka: npci.outbound.request (type=BALANCE)
     -> NpciAdapterService.processBalance()
        -> XmlBuilderService.buildReqBalEnq()
        -> HTTP POST to NPCI /upi/ReqBalEnq endpoint
           -> NPCI responds synchronously with balance data
              -> XmlParserService.parseRespBalEnq()
                 -> Kafka: npci.inbound.response (balance=25000.00, currency=INR)
```

### Flow C — INBOUND COLLECT (NPCI-Initiated)

Used when NPCI initiates a collect/credit request on behalf of another PSP.

```
NPCI (external)
  -> POST /upi/ReqPay/1.0/urn:txnid/{txnId} (type=COLLECT)
     -> NpciWebhookController.handleReqPay()
        -> HTTP 200 Ack returned immediately to NPCI
        -> @Async: XmlParserService.parseReqPay()
           -> NpciResponseProducer.publish()
              -> Kafka: npci.inbound.response (type=COLLECT)
                 -> Transaction Orchestrator decides accept/reject
```

---

## 6. Kafka Topics

| Topic | Direction | Producer | Consumer |
|---|---|---|---|
| `npci.outbound.request` | Inbound to Adapter | Transaction Orchestrator | NPCI Adapter |
| `npci.inbound.response` | Outbound from Adapter | NPCI Adapter | Transaction Orchestrator, Notification Service |

### Consumed Event Schema — `npci.outbound.request`

`NpciOutboundRequestEvent` fields:

| Field | Type | Description |
|---|---|---|
| `txnId` | String | PSP internal transaction identifier |
| `msgId` | String | UUID for this specific NPCI message |
| `type` | String | `PAY`, `BALANCE`, or `REVERSAL` |
| `payerVpa` | String | Payer UPI VPA |
| `payeeVpa` | String | Payee UPI VPA |
| `amount` | String | Amount in decimal string format |
| `timestamp` | String | ISO-8601 event timestamp |

### Produced Event Schema — `npci.inbound.response`

`NpciInboundResponseEvent` fields:

| Field | Type | Description |
|---|---|---|
| `txnId` | String | PSP internal transaction identifier |
| `msgId` | String | Echo of the originating message ID |
| `type` | String | `PAY`, `BALANCE`, `COLLECT`, or `REVERSAL` |
| `result` | String | `SUCCESS`, `FAILURE`, `TIMEOUT`, or `DEEMED` |
| `balance` | String | Account balance (for BALANCE type only) |
| `currency` | String | Currency code |
| `errCode` | String | NPCI error code if result is FAILURE |
| `timestamp` | String | ISO-8601 response timestamp |

---

## 7. REST Endpoints (Webhook Receivers)

These endpoints are called by NPCI. Their URLs are pre-registered with NPCI and are not passed in outbound messages.

| Method | Path | Flow | Description |
|---|---|---|---|
| POST | `/upi/RespPay/1.0/urn:txnid/{txnId}` | A | Receives NPCI's asynchronous payment result (RespPay) |
| POST | `/upi/ReqPay/1.0/urn:txnid/{txnId}` | C | Receives NPCI-initiated collect or credit request (ReqPay) |

**Response:** All webhook endpoints return an XML Ack (HTTP 200) immediately before any processing begins. This is the "ack-first" pattern, which prevents NPCI from retrying due to slow internal processing.

---

## 8. Service Details

### 8.1 NpciAdapterService

Orchestrates the complete outbound NPCI interaction for a single event. For Flow A (PAY):
1. Calls `XmlBuilderService.buildReqPay()` to construct the NPCI-format XML
2. Calls `SigningService.sign()` to produce the `X-UPI-Signature` header value
3. Issues the HTTP POST using the configured `RestTemplate`
4. Parses the synchronous Ack response to confirm NPCI received the request
5. Returns control; the actual result will arrive via the RespPay webhook

### 8.2 XmlBuilderService

Constructs NPCI-compliant XML messages. In the demo implementation, abbreviated tag names are used for clarity. Production must conform to the full NPCI UPI 2.x XSD schema.

Produces:
- `ReqPay` XML for payment requests
- `ReqBalEnq` XML for balance enquiries
- `ReqChkTxn` XML for status queries

### 8.3 XmlParserService

Parses NPCI XML callback payloads into internal Java event objects. Extracts `result`, `errCode`, `txnId`, and `timestamp` from the XML tree.

### 8.4 SigningService

- **Demo implementation:** SHA-256 hash of the XML body, hex-encoded via Apache Commons Codec. Set in `X-UPI-Signature` header.
- **Production implementation:** ECDSA P-256 digital signature using the PSP's HSM-stored private key. The `sign(String xmlBody)` interface is unchanged; only the implementation body swaps.

### 8.5 EncryptionService

- **Demo implementation:** Base64 encoding of the MPIN field.
- **Production implementation:** AES-256-GCM encryption of the MPIN using NPCI's public key. The `encryptMpin(String mpin)` interface is unchanged.

### 8.6 IdempotencyService (Adapter-local)

Prevents duplicate Kafka publishes when NPCI retries a callback that was already processed. Uses an in-memory `ConcurrentHashMap<String, Boolean>` keyed by `txnId`.

**Limitation:** In-memory; not safe for multi-instance deployments. Production upgrade: Redis SET with TTL.

---

## 9. Configuration

| Property | Description |
|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `NPCI_BASE_URL` | NPCI endpoint base URL |
| `NPCI_MTLS_ENABLED` | Set to `true` to enable mTLS for NPCI transport |
| `NPCI_KEYSTORE_PATH` | Path to PKCS12 keystore file |
| `NPCI_TRUSTSTORE_PATH` | Path to PKCS12 truststore file |

---

## 10. Demo versus Production Comparison

| Component | Demo | Production |
|---|---|---|
| Signing | SHA-256 hex | ECDSA P-256 with PSP private key in HSM |
| MPIN Encryption | Base64 | AES-256-GCM with NPCI public key |
| Transport | Plain HTTP | mTLS (flip `NPCI_MTLS_ENABLED=true`) |
| Idempotency | In-memory ConcurrentHashMap | Redis SET (multi-instance safe) |
| XML Schema | Abbreviated demo tags | Full NPCI UPI 2.x XSD-compliant XML |

---

## 11. Building the Service

```bash
cd services/psp-switch/npci-adapter
mvn clean package -DskipTests
```

The service JAR is output to `target/npci-adapter-1.0.0.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).

---

## 12. Manual Webhook Testing

The following examples demonstrate the XML payload structure expected by each webhook endpoint. The target host and port will match the configured service deployment address.

**Simulate NPCI RespPay callback (Flow A — payment result):**

```xml
POST /upi/RespPay/1.0/urn:txnid/test-001
Content-Type: application/xml
X-UPI-Signature: <signature>

<?xml version="1.0" encoding="UTF-8"?>
<RespPay>
  <Head msgId="msg-001" orgId="NPCI"/>
  <Txn id="test-001" result="SUCCESS" errCode=""/>
</RespPay>
```

**Simulate NPCI-initiated collect (Flow C):**

```xml
POST /upi/ReqPay/1.0/urn:txnid/collect-001
Content-Type: application/xml
X-UPI-Signature: <signature>

<?xml version="1.0" encoding="UTF-8"?>
<ReqPay>
  <Head msgId="msg-002" orgId="NPCI"/>
  <Txn id="collect-001" type="COLLECT" ts="2026-04-18T10:30:00Z">
    <Payer addr="user@demopsp" type="PERSON">
      <Amount cur="INR" value="250.00"/>
    </Payer>
    <Payees>
      <Payee addr="merchant@okaxis" type="ENTITY">
        <Amount cur="INR" value="250.00"/>
      </Payee>
    </Payees>
  </Txn>
</ReqPay>
```

```

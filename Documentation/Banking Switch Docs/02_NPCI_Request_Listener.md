# NPCI Request Listener

## 1. Purpose

The NPCI Request Listener is the single entry point for all inbound UPI traffic directed at the Banking Switch from the NPCI network. It exposes three HTTP endpoints that accept UPI-compliant XML payloads, validates each request at a structural level, extracts the transaction identifier, and publishes a typed event onto the Kafka `banking.inbound.txn` topic. Once the event is published, the listener returns a synchronous XML acknowledgement to NPCI.

No business logic is executed in this service. Its sole responsibilities are reception, basic validation, XML parsing, and event publication.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `npci-request-listener` |
| Group ID | `com.bankingswitch` |
| Base Package | `com.bankingswitch.listener` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8080 (Banking Switch VM) |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST endpoint exposure |
| `spring-kafka` | Kafka producer for event publication |
| `jackson-dataformat-xml` | XML-to-Java deserialization via XmlMapper |
| `lombok` | Boilerplate reduction |
| `spring-boot-starter-test` | Unit test support |

---

## 4. Source Structure

```
com.bankingswitch.listener
├── NpciRequestListenerApplication.java
├── config/
│   └── KafkaProducerConfig.java          Kafka producer factory and template configuration
├── controller/
│   └── NpciInboundController.java        HTTP endpoints for ReqBalEnq, ReqPay, ReqCredit
├── model/
│   └── InboundTransactionEvent.java      Kafka event payload model
├── producer/
│   └── TransactionEventProducer.java     Publishes InboundTransactionEvent to Kafka
└── service/
    ├── RequestValidationService.java     Validates that the XML contains a <Txn> element
    └── XmlParsingService.java            Extracts the txnId from <Txn id="...">
```

---

## 5. REST Endpoints

All endpoints are mounted under the base path `/bank/upi`. Each accepts and produces `application/xml`.

| Method | Path | UPI Message Type | Description |
|---|---|---|---|
| POST | `/bank/upi/ReqBalEnq` | `ReqBalEnq` | Balance enquiry from NPCI |
| POST | `/bank/upi/ReqPay` | `ReqPay` | Payment debit request (bank is Payer's bank) |
| POST | `/bank/upi/ReqCredit` | `ReqCredit` | Credit request (bank is Payee's bank) |

### Request Format

All endpoints receive a raw UPI XML payload in the request body. No JSON is involved at this layer. Example for a balance enquiry:

```xml
POST /bank/upi/ReqBalEnq
Content-Type: application/xml

<?xml version="1.0" encoding="UTF-8"?>
<ReqBalEnq>
  <Head msgId="msg-001" orgId="NPCI" ts="2026-06-25T10:00:00Z"/>
  <Txn id="txn-bal-001" ts="2026-06-25T10:00:00Z">
    <Payer addr="user@bankdemo" type="PERSON">
      <Amount cur="INR" value="0.00"/>
    </Payer>
  </Txn>
</ReqBalEnq>
```

### Acknowledgement Response

A successful response is returned immediately after the event is published to Kafka:

```xml
<Ack api="ReqBalEnq" reqMsgId="txn-bal-001" err="" ts="2026-06-25T10:00:00.123Z"/>
```

A validation failure returns HTTP 400 with:

```xml
<Ack api="ReqBalEnq" err="INVALID_XML"/>
```

---

## 6. Request Processing Flow

```
NPCI
  -> POST /bank/upi/{TxnType}
     -> NpciInboundController.processRequest()
        -> RequestValidationService.validate(xmlPayload)
           [if invalid] -> return HTTP 400 <Ack err="INVALID_XML"/>
           [if valid]
        -> XmlParsingService.extractTxnId(xmlPayload)
           -> Reads rootNode.Txn.id
        -> Build InboundTransactionEvent {txnId, txnType, xmlPayload, timestamp}
        -> TransactionEventProducer.sendEvent(event)
           -> Kafka topic: banking.inbound.txn
        -> Return HTTP 200 <Ack api="..." reqMsgId="..." err="" ts="..."/>
```

---

## 7. Validation Logic

`RequestValidationService` applies the following checks in order:

| Check | Condition | Action on Failure |
|---|---|---|
| Null / empty payload | `xml == null` or `xml.isEmpty()` | Return `false` (HTTP 400) |
| Structural presence | XML does not contain a `<Txn` element | Return `false` (HTTP 400) |

No XSD schema validation is applied in the current implementation. This is intentional for the demo system. A production deployment must validate against the NPCI UPI 2.x XSD.

---

## 8. XML Parsing

`XmlParsingService` uses Jackson's `XmlMapper` to parse the raw XML string into a `JsonNode` tree. It then extracts the transaction identifier via:

```
rootNode -> "Txn" -> "id"
```

If the element is missing or the XML cannot be parsed, the method returns the string `"UNKNOWN"`. The downstream Orchestrator records the transaction with this identifier; a value of `"UNKNOWN"` indicates an upstream parsing failure that should be investigated.

---

## 9. Kafka Event — `InboundTransactionEvent`

Published to topic: `banking.inbound.txn`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier extracted from `<Txn id="...">` |
| `txnType` | String | `ReqBalEnq`, `ReqPay`, or `ReqCredit` |
| `xmlPayload` | String | Full raw XML payload as received from NPCI |
| `timestamp` | long | Unix epoch milliseconds at time of receipt |

---

## 10. Configuration Properties

| Property | Description |
|---|---|
| `spring.kafka.bootstrap-servers` | Kafka broker address (IP:port of the Kafka host VM) |
| `kafka.topic.inbound-txn` | Name of the Kafka topic to publish events to (default: `banking.inbound.txn`) |
| `server.port` | HTTP listening port |

All properties are supplied via `application.yml` or environment variable overrides. No hardcoded addresses are present in the source code.

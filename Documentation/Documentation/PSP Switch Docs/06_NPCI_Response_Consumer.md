# NPCI Response Consumer

## 1. Purpose

The NPCI Response Consumer is a lightweight boundary service that receives raw XML callback payloads from NPCI over HTTP, parses them into structured internal events, and publishes those events to Kafka. It acts as the protocol translation layer between the NPCI XML callback interface and the PSP Switch's internal JSON event bus.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `npci-response-consumer` |
| Group ID | `com.pspswitch` |
| Version | `1.0.0` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8084 |
| Base Package | `com.pspswitch.npciresponse` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | REST endpoints for NPCI callbacks |
| spring-kafka | Kafka producer |
| spring-boot-starter-actuator | Health checks |
| jackson-databind | JSON serialization |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Testing |
| spring-kafka-test | Embedded Kafka for tests |

---

## 4. Source Structure

```
com.pspswitch.npciresponse
├── NpciResponseConsumerApplication.java
├── config/
│   └── KafkaConfig.java                 ProducerFactory and KafkaTemplate configuration
├── controller/
│   └── NpciCallbackController.java      POST /npci/callback (receives NPCI XML)
├── model/
│   └── NpciInboundResponseEvent.java    Internal event schema published to Kafka
├── parser/
│   └── NpciXmlParser.java               XML-to-event parser
├── producer/
│   └── NpciResponseKafkaProducer.java   Publishes structured events to Kafka
└── service/
    └── NpciCallbackService.java         Orchestrates parse -> publish flow
```

---

## 5. Processing Flow

```
NPCI External System
  -> HTTP POST /npci/callback
     Content-Type: application/xml
     -> NpciCallbackController.receiveCallback()
        -> HTTP 200 returned immediately (ack-first pattern)
        -> NpciCallbackService.process(xmlBody)
           -> NpciXmlParser.parse(xmlBody)
              -> NpciInboundResponseEvent constructed
              -> NpciResponseKafkaProducer.publish(event)
                 -> Kafka: npci.inbound.response
```

---

## 6. REST Endpoint

| Method | Path | Description |
|---|---|---|
| POST | `/npci/callback` | Receives XML callback from NPCI |

The endpoint immediately returns HTTP 200 before invoking the parser and producer. This ack-first pattern is mandatory to prevent NPCI from retrying due to slow processing on the PSP side.

---

## 7. Event Model — NpciInboundResponseEvent

| Field | Type | Description |
|---|---|---|
| `txnId` | String | PSP internal transaction identifier extracted from XML |
| `msgId` | String | NPCI message identifier |
| `type` | String | `PAY`, `BALANCE`, `COLLECT`, or `REVERSAL` |
| `result` | String | `SUCCESS`, `FAILURE`, `TIMEOUT`, or `DEEMED` |
| `balance` | String | Account balance (only for BALANCE type) |
| `currency` | String | Currency code |
| `errCode` | String | NPCI error code on FAILURE result |
| `timestamp` | String | ISO-8601 timestamp of the NPCI response |

---

## 8. XML Parser — NpciXmlParser

`NpciXmlParser` accepts a raw XML string and extracts the fields required to populate `NpciInboundResponseEvent`. It uses Java's built-in `DocumentBuilder` (JAXP) to parse the XML DOM.

Key parsing logic:
- Reads the `<Head>` element for `msgId`
- Reads the `<Txn>` element for `id` (txnId), `result`, and `errCode`
- Reads optional `<Amount>` child for balance enquiry responses
- On any parse error, logs the exception and returns a response event with `result=TIMEOUT` to signal the Orchestrator that the response is indeterminate

---

## 9. Kafka Producer — NpciResponseKafkaProducer

Publishes the parsed `NpciInboundResponseEvent` as a JSON string to the `npci.inbound.response` topic.

| Topic | Key | Value |
|---|---|---|
| `npci.inbound.response` | `txnId` | JSON-serialized `NpciInboundResponseEvent` |

---

## 10. Configuration

| Property | Description |
|---|---|
| `spring.kafka.bootstrap-servers` | Kafka broker address(es) |
| `server.port` | Service HTTP port |

---

## 11. Building the Service

```bash
cd services/psp-switch/npci-response-consumer
mvn clean package -DskipTests
```

The service JAR is output to `target/npci-response-consumer-1.0.0.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).


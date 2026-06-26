# NPCI Response Adapter

## 1. Purpose

The NPCI Response Adapter is the outbound gateway of the Banking Switch. It consumes completed transaction result events from the Kafka `banking.npci.response` topic, constructs a UPI-compliant XML response body, and dispatches it to the NPCI network via an HTTP POST callback.

This service is the counterpart to the NPCI Request Listener. Together they form the inbound-to-outbound boundary of the Banking Switch's interaction with NPCI.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `npci-response-adapter` |
| Group ID | `com.bankingswitch` |
| Parent Artifact | `payment-switch` |
| Base Package | `com.bankingswitch.npciadapter` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 8083 (Banking Switch VM) |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | RestTemplate for HTTP callback dispatch |
| `spring-kafka` | Kafka consumer |
| `lombok` | Boilerplate reduction |
| `spring-boot-starter-test` | Unit test support |

---

## 4. Source Structure

```
com.bankingswitch.npciadapter
├── NpciResponseAdapterApplication.java
├── config/
│   └── RestClientConfig.java             Spring bean definition for RestTemplate
├── consumer/
│   └── NpciResponseConsumer.java         @KafkaListener on banking.npci.response
├── model/
│   └── NpciCallbackEvent.java            Consumed event model
└── service/
    ├── XmlResponseBuilderService.java    Builds UPI-compliant XML response strings
    └── NpciCallbackService.java          Dispatches HTTP POST callbacks to NPCI
```

---

## 5. Processing Flow

```
Kafka topic: banking.npci.response
    -> NpciResponseConsumer.consume(NpciCallbackEvent)
       -> XmlResponseBuilderService.buildResponseXml(event)
          -> Constructs XML body (RespBalEnq, RespPay, or RespCredit)
       -> NpciCallbackService.sendCallback(txnType, txnId, xmlResponse)
          -> HTTP POST to {npci.callback-url}/{txnType}/{txnId}
             Content-Type: application/xml
```

---

## 6. `XmlResponseBuilderService`

Constructs the UPI XML response body from a `NpciCallbackEvent`. The format is determined by the `txnType` field of the event.

### Output XML Format

```xml
<{txnType}>
  <Txn id="{txnId}" type="{txnType}"/>
  <Resp result="{SUCCESS|FAILURE}" errCode="{errorCode}"/>
  <BalDetail settlementAmount="{balance}" settAmount="{balance}"/>
</{txnType}>
```

- `result` is set to `SUCCESS` if the event `status` is `"SUCCESS"`, otherwise `FAILURE`.
- `errCode` is empty if no error code is present.
- `settlementAmount` and `settAmount` are set to the `balance` value from the event, or `0.0` if null.

### Supported `txnType` Values

| `txnType` Field | XML Root Tag | Trigger |
|---|---|---|
| `RespBalEnq` | `<RespBalEnq>` | Balance enquiry result |
| `RespPay` | `<RespPay>` | Payment (debit) result |
| `RespCredit` | `<RespCredit>` | Credit result |

---

## 7. `NpciCallbackService`

Dispatches the XML response to the NPCI callback URL.

### Callback URL Construction

```
{npci.callback-url}/{txnType}/{txnId}
```

The `npci.callback-url` base is injected from configuration. No hardcoded addresses exist in the source code.

### Request Details

| Attribute | Value |
|---|---|
| Method | POST |
| Content-Type | `application/xml` |
| Body | XML string produced by `XmlResponseBuilderService` |

### Error Handling

If the HTTP POST fails (network error, NPCI unreachable, non-2xx response), the error is logged and execution continues. The Banking Switch does not retry failed NPCI callbacks in the current implementation.

Production consideration: A retry mechanism with exponential backoff should be introduced for NPCI callback failures to handle transient network issues between the Banking Switch VM and the NPCI VM.

---

## 8. Kafka Event — `NpciCallbackEvent`

Consumed from topic: `banking.npci.response`

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Transaction identifier |
| `txnType` | String | `RespBalEnq`, `RespPay`, or `RespCredit` |
| `status` | String | `SUCCESS` or failure indicator |
| `errorCode` | String | Error code if the CBS operation failed |
| `balance` | Double | Account balance for balance enquiry responses |
| `xmlPayload` | String | Original inbound XML (not used in callback, available for logging) |

---

## 9. Configuration Properties

| Property | Description |
|---|---|
| `npci.callback-url` | Base URL for NPCI callback delivery (format: `http://<NPCI-VM-IP>:<port>`) |
| `spring.kafka.bootstrap-servers` | Kafka broker address |
| `kafka.topic.npci-response` | Kafka topic to consume from |

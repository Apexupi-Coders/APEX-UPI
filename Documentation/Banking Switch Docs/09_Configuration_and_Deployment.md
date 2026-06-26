# Configuration and Deployment — Banking Switch

## 1. Deployment Overview

The Banking Switch consists of five Spring Boot services. All services run on dedicated VMs with no containerization. Each service is packaged as an executable JAR and started as a foreground or background Java process.

---

## 2. VM Topology

### Local Deployment (VirtualBox)

The APEX-UPI system is deployed across four separate VirtualBox virtual machines. The Banking Switch occupies its own dedicated VM.

| VM | Hosted Services |
|---|---|
| VM 1 — PSP Switch | All PSP Switch microservices, Apache Kafka broker, PostgreSQL for PSP |
| VM 2 — Banking Switch | NPCI Request Listener, Transaction Orchestrator, CBS Adapter, NPCI Response Adapter, Bank Ledger Service, PostgreSQL for `bankswitch_db` |
| VM 3 — Core Banking System | CBS service, PostgreSQL for `cbs_db` |
| VM 4 — NPCI | NPCI simulator service |

### Cloud Deployment (Azure)

In the Azure deployment, services are consolidated into three virtual machines. Banking Switch services and the CBS service share a single VM.

| VM | Hosted Services |
|---|---|
| VM 1 — PSP Switch | All PSP Switch microservices, Apache Kafka broker, PostgreSQL for PSP |
| VM 2 — NPCI Network | NPCI simulator/network service |
| VM 3 — Banking Services | Banking Switch microservices + CBS service, shared PostgreSQL instance |

> In the Azure configuration, the CBS Adapter on VM 3 communicates with the CBS service also on VM 3. The `cbs.host` property uses the internal IP address of VM 3 rather than a cross-VM address.

---

## 3. Prerequisites

All VMs must have the following installed before service startup:

| Prerequisite | Required By |
|---|---|
| Java 17 (JDK) | All Banking Switch services |
| Apache Kafka | PSP Switch VM (Kafka broker is hosted there) |
| PostgreSQL 14+ | Banking Switch VM — for `bankswitch_db` |
| Maven 3.8+ | Build-time only (not required at runtime) |

---

## 4. Database Setup

Run the following SQL on the PostgreSQL instance of the Banking Switch VM to create the required database and tables.

```sql
CREATE DATABASE bankswitch_db;

\c bankswitch_db

CREATE TABLE transactions (
    txn_id      VARCHAR(255) PRIMARY KEY,
    txn_type    VARCHAR(50),
    state       VARCHAR(50),
    payer_vpa   VARCHAR(255),
    payee_vpa   VARCHAR(255),
    amount      DOUBLE PRECISION,
    xml_payload TEXT,
    created_at  BIGINT,
    updated_at  BIGINT
);

CREATE TABLE event_log (
    id          SERIAL PRIMARY KEY,
    txn_id      VARCHAR(255),
    event_type  VARCHAR(50),
    event_data  TEXT,
    timestamp   BIGINT
);

CREATE TABLE bank_ledger (
    id          SERIAL PRIMARY KEY,
    txn_id      VARCHAR(255),
    account     VARCHAR(255),
    entry_type  VARCHAR(50),
    amount      DOUBLE PRECISION,
    status      VARCHAR(50)
);
```

The setup script is also available at:
`services/banking-switch/setup/create-db.sql`

---

## 5. Building the Services

Each service is built independently using Maven. Run the following from the repository root:

```bash
# NPCI Request Listener
cd services/banking-switch/npci-request-listener
mvn clean package -DskipTests

# Transaction Orchestrator
cd services/banking-switch/orchestrator
mvn clean package -DskipTests

# CBS Adapter
cd services/banking-switch/cbs-adapter
mvn clean package -DskipTests

# NPCI Response Adapter
cd services/banking-switch/npci-response-adapter
mvn clean package -DskipTests

# Bank Ledger Service
cd services/banking-switch/bank-ledger-service
mvn clean package -DskipTests
```

Each build produces a runnable JAR in the `target/` subdirectory of the respective service.

---

## 6. Configuration Properties

Each service is configured via `application.yml` (or environment variable overrides). The following tables describe the key configuration properties for each service. Replace all placeholder values with the actual IP addresses of the respective VMs.

### 6.1 NPCI Request Listener

| Property | Description | Example Value |
|---|---|---|
| `server.port` | HTTP listening port | `8080` |
| `spring.kafka.bootstrap-servers` | Kafka broker address on the PSP Switch VM | `<PSP-Switch-VM-IP>:9092` |
| `kafka.topic.inbound-txn` | Kafka topic for inbound events | `banking.inbound.txn` |

### 6.2 Transaction Orchestrator

| Property | Description | Example Value |
|---|---|---|
| `server.port` | HTTP listening port | `8081` |
| `spring.datasource.url` | JDBC URL for `bankswitch_db` | `jdbc:postgresql://<Banking-Switch-VM-IP>:5432/bankswitch_db` |
| `spring.datasource.username` | PostgreSQL username | `postgres` |
| `spring.datasource.password` | PostgreSQL password | (set per environment) |
| `spring.kafka.bootstrap-servers` | Kafka broker address | `<PSP-Switch-VM-IP>:9092` |
| `kafka.topic.inbound-txn` | Consumed topic | `banking.inbound.txn` |
| `kafka.topic.cbs-request` | Produced topic | `banking.cbs.request` |
| `kafka.topic.cbs-response` | Consumed topic | `banking.cbs.response` |
| `kafka.topic.npci-response` | Produced topic | `banking.npci.response` |

### 6.3 CBS Adapter

| Property | Description | Example Value |
|---|---|---|
| `server.port` | HTTP listening port | `8082` |
| `cbs.host` | Base URL of the CBS service | `http://<CBS-VM-IP>:9090` |
| `spring.kafka.bootstrap-servers` | Kafka broker address | `<PSP-Switch-VM-IP>:9092` |
| `kafka.topic.cbs-request` | Consumed topic | `banking.cbs.request` |
| `kafka.topic.cbs-response` | Produced topic | `banking.cbs.response` |

### 6.4 NPCI Response Adapter

| Property | Description | Example Value |
|---|---|---|
| `server.port` | HTTP listening port | `8083` |
| `npci.callback-url` | Base URL for NPCI callback | `http://<NPCI-VM-IP>:<port>` |
| `spring.kafka.bootstrap-servers` | Kafka broker address | `<PSP-Switch-VM-IP>:9092` |
| `kafka.topic.npci-response` | Consumed topic | `banking.npci.response` |

### 6.5 Bank Ledger Service

| Property | Description | Example Value |
|---|---|---|
| `server.port` | HTTP listening port | `8084` |
| `spring.datasource.url` | JDBC URL for `bankswitch_db` | `jdbc:postgresql://<Banking-Switch-VM-IP>:5432/bankswitch_db` |
| `spring.datasource.username` | PostgreSQL username | `postgres` |
| `spring.datasource.password` | PostgreSQL password | (set per environment) |
| `spring.kafka.bootstrap-servers` | Kafka broker address | `<PSP-Switch-VM-IP>:9092` |

---

## 7. Service Startup

All services are started by executing their built JAR files with Java. The startup order must be respected to avoid connection errors on startup.

### Required Startup Order

1. PostgreSQL (Banking Switch VM) — must be running before services 3 and 5
2. Apache Kafka (PSP Switch VM) — must be running before all Banking Switch services
3. NPCI Request Listener
4. Transaction Orchestrator
5. CBS Adapter
6. NPCI Response Adapter
7. Bank Ledger Service

### Start Commands

```bash
# NPCI Request Listener
java -jar services/banking-switch/npci-request-listener/target/npci-request-listener-*.jar \
  --spring.config.location=<path-to-application.yml>

# Transaction Orchestrator
java -jar services/banking-switch/orchestrator/target/orchestrator-*.jar \
  --spring.config.location=<path-to-application.yml>

# CBS Adapter
java -jar services/banking-switch/cbs-adapter/target/cbs-adapter-*.jar \
  --spring.config.location=<path-to-application.yml>

# NPCI Response Adapter
java -jar services/banking-switch/npci-response-adapter/target/npci-response-adapter-*.jar \
  --spring.config.location=<path-to-application.yml>

# Bank Ledger Service
java -jar services/banking-switch/bank-ledger-service/target/bank-ledger-service-*.jar \
  --spring.config.location=<path-to-application.yml>
```

A convenience script is available at:
`services/banking-switch/setup/start-all.sh` (Linux/macOS)
`services/banking-switch/setup/start-all.ps1` (Windows)

---

## 8. Port Summary

| Service | Default Port |
|---|---|
| NPCI Request Listener | 8080 |
| Transaction Orchestrator | 8081 |
| CBS Adapter | 8082 |
| NPCI Response Adapter | 8083 |
| Bank Ledger Service | 8084 |
| PostgreSQL (`bankswitch_db`) | 5432 |

---

## 9. Verifying Service Health

After startup, verify each service is running by checking the Spring Boot health endpoint if Actuator is configured, or by observing the JVM startup log for the message:

```
Started <ServiceName>Application in X.XXX seconds
```

To verify Kafka connectivity, confirm that the Orchestrator log shows successful consumer group registration for both `banking.inbound.txn` and `banking.cbs.response`.

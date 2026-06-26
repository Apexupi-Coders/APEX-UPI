# Configuration and Deployment — Core Banking System

## 1. Deployment Overview

The Core Banking System is a single Spring Boot service packaged as an executable JAR. It runs on a dedicated VM and is called exclusively over HTTP by the CBS Adapter of the Banking Switch. It requires no messaging infrastructure (no Kafka dependency) and has a direct dependency only on its PostgreSQL database.

---

## 2. VM Topology

### Local Deployment (VirtualBox)

The CBS runs on a dedicated virtual machine separate from the Banking Switch:

| VM | Services Hosted |
|---|---|
| VM 1 — PSP Switch | All PSP Switch microservices, Apache Kafka broker, PostgreSQL for PSP |
| VM 2 — Banking Switch | All Banking Switch microservices, PostgreSQL for `bankswitch_db` |
| VM 3 — Core Banking System | CBS service (`cbs-service`), PostgreSQL for `cbs_db` |
| VM 4 — NPCI | NPCI simulator/network service |

In this topology, the CBS Adapter on VM 2 communicates with the CBS service on VM 3 over the VirtualBox host-only or bridged network.

### Cloud Deployment (Azure)

In the Azure deployment, the CBS is co-located with the Banking Switch on a shared Banking Services VM:

| VM | Services Hosted |
|---|---|
| VM 1 — PSP Switch | All PSP Switch microservices, Apache Kafka broker, PostgreSQL for PSP |
| VM 2 — NPCI Network | NPCI simulator/network service |
| VM 3 — Banking Services | Banking Switch microservices + CBS service, shared PostgreSQL instance |

In this configuration the CBS Adapter and the CBS service communicate over the internal loopback or internal Azure VM network interface. The `cbs.host` property in the CBS Adapter configuration points to the internal address of VM 3.

---

## 3. Prerequisites

| Prerequisite | Version | Notes |
|---|---|---|
| Java 17 (JRE or JDK) | 17+ | Required to run the JAR |
| PostgreSQL | 14+ | Must be running and accessible before CBS starts |
| Maven 3.8+ | 3.8+ | Required at build time only |

---

## 4. Database Setup

Run the following SQL on the PostgreSQL instance of the CBS VM to create the required database and tables. The initial database creation script is available at `services/cbs/setup/create-db.sql`.

```sql
CREATE DATABASE cbs_db;

\c cbs_db

CREATE TABLE accounts (
    vpa             VARCHAR(255) PRIMARY KEY,
    account_number  VARCHAR(255),
    balance         DECIMAL(19, 4),
    status          VARCHAR(50),
    pin_hash        VARCHAR(255),
    version         BIGINT
);

CREATE TABLE transaction_ledger (
    id              SERIAL PRIMARY KEY,
    txn_id          VARCHAR(255),
    vpa             VARCHAR(255),
    type            VARCHAR(10),
    amount          DECIMAL(19, 4),
    balance_before  DECIMAL(19, 4),
    balance_after   DECIMAL(19, 4),
    timestamp       TIMESTAMP
);

CREATE TABLE user_credentials (
    vpa         VARCHAR(255) PRIMARY KEY,
    mpin_hash   VARCHAR(255),
    device_id   VARCHAR(255)
);
```

### Seed Data

The `accounts` table must be pre-populated with test accounts before the system can process any transactions. Insert accounts using standard SQL INSERT statements. Example:

```sql
INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('user@bankdemo', 'ACC100001', 50000.00, 'ACTIVE', '<pin_hash_value>', 0);

INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('payee@bankdemo', 'ACC100002', 10000.00, 'ACTIVE', '<pin_hash_value>', 0);
```

---

## 5. Building the Service

```bash
cd services/cbs
mvn clean package -DskipTests
```

The build produces:
```
services/cbs/target/cbs-service-0.0.1-SNAPSHOT.jar
```

---

## 6. Configuration Properties

The CBS service is configured via `application.yml`. All network addresses must use the actual IP of the CBS VM (or the internal address in Azure). No placeholder addresses are used in the source code.

| Property | Description | Example Value |
|---|---|---|
| `server.port` | HTTP port the CBS service listens on | `9090` |
| `spring.datasource.url` | JDBC URL for `cbs_db` | `jdbc:postgresql://<CBS-VM-IP>:5432/cbs_db` |
| `spring.datasource.username` | PostgreSQL username | `postgres` |
| `spring.datasource.password` | PostgreSQL password | (set per environment) |
| `spring.datasource.driver-class-name` | JDBC driver | `org.postgresql.Driver` |
| `spring.jpa.hibernate.ddl-auto` | Schema management | `validate` (production) or `update` (development) |
| `spring.jpa.show-sql` | Log SQL queries | `false` (production) |

### Example `application.yml`

```yaml
server:
  port: 9090

spring:
  datasource:
    url: jdbc:postgresql://<CBS-VM-IP>:5432/cbs_db
    username: postgres
    password: <password>
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
```

---

## 7. Starting the Service

```bash
java -jar services/cbs/target/cbs-service-0.0.1-SNAPSHOT.jar \
  --spring.config.location=<path-to-application.yml>
```

The CBS service must be started before the CBS Adapter on the Banking Switch VM, as the Adapter will attempt to connect to the CBS REST API immediately upon receiving a Kafka event.

### Startup Verification

Confirm the service is running by observing the Spring Boot startup log:

```
Started CbsServiceApplication in X.XXX seconds
```

Then verify the balance endpoint is reachable from the Banking Switch VM:

```bash
curl http://<CBS-VM-IP>:9090/cbs/balance/user@bankdemo
```

Expected response:
```json
{"vpa":"user@bankdemo","balance":50000.00,"status":"SUCCESS"}
```

---

## 8. Port Summary

| Service | Default Port |
|---|---|
| CBS Service | 9090 |
| PostgreSQL (`cbs_db`) | 5432 |

---

## 9. Setup Scripts

The following convenience scripts are provided for automated setup:

| Script | Location | Purpose |
|---|---|---|
| `create-db.sql` | `services/cbs/setup/` | Creates `cbs_db` database |
| `install.sh` | `services/cbs/setup/` | Full install script (Linux/macOS) |
| `install.ps1` | `services/cbs/setup/` | Full install script (Windows/PowerShell) |

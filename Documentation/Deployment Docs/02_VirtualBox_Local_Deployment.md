# VirtualBox Local Deployment

## 1. Overview

This guide covers the complete deployment of APEX-UPI on four Oracle VirtualBox virtual machines for local development and testing. Each VM hosts one logical subsystem with full network isolation between them.

---

## 2. VM Specifications

All four VMs run the same base operating system. The recommended minimum hardware allocation per VM is:

| VM | Name | OS | vCPUs | RAM | Disk |
|---|---|---|---|---|---|
| VM 1 | PSP Switch | Ubuntu 22.04 LTS / Windows Server | 4 | 8 GB | 40 GB |
| VM 2 | Banking Switch | Ubuntu 22.04 LTS / Windows Server | 2 | 4 GB | 20 GB |
| VM 3 | Core Banking System | Ubuntu 22.04 LTS / Windows Server | 2 | 4 GB | 20 GB |
| VM 4 | NPCI | Ubuntu 22.04 LTS / Windows Server | 2 | 4 GB | 20 GB |

VM 1 has higher resource requirements because it hosts Kafka, ZooKeeper, Redis, and nine microservices.

---

## 3. Network Configuration

All VMs must be on the same VirtualBox network and able to reach each other over TCP/IP. Two VirtualBox network modes are suitable:

| Mode | When to Use |
|---|---|
| Host-Only Adapter | VMs communicate with each other and the host machine only. No external internet access. Suitable for isolated testing. |
| Bridged Adapter | VMs receive IP addresses from the host's physical network. VMs are reachable from any machine on the LAN. Suitable when external tools need to connect. |

Each VM will receive a static or DHCP-assigned IP address. In this document, placeholders are used:

| Placeholder | Meaning |
|---|---|
| `<PSP_VM_IP>` | IP address of VM 1 (PSP Switch) |
| `<BANK_VM_IP>` | IP address of VM 2 (Banking Switch) |
| `<CBS_VM_IP>` | IP address of VM 3 (Core Banking System) |
| `<NPCI_VM_IP>` | IP address of VM 4 (NPCI) |

---

## 4. VM 1 -- PSP Switch

### 4.1 Prerequisites

Install the following on VM 1:

| Software | Version | Installation |
|---|---|---|
| Java JDK | 17+ | `sudo apt install openjdk-17-jdk` (Ubuntu) |
| PostgreSQL | 14+ | `sudo apt install postgresql` |
| Apache Kafka | 3.x+ | Download from https://kafka.apache.org/downloads |
| Redis | 7+ | `sudo apt install redis-server` |
| Maven | 3.8+ | `sudo apt install maven` |

### 4.2 Database Setup

```bash
sudo -u postgres psql
```

```sql
CREATE DATABASE apexupi;
CREATE USER apexupi WITH PASSWORD 'apexupi';
GRANT ALL PRIVILEGES ON DATABASE apexupi TO apexupi;
```

Table schemas are auto-created by JPA (`ddl-auto: update`) on first service startup.

### 4.3 Start Infrastructure

```bash
# Start ZooKeeper
bin/zookeeper-server-start.sh config/zookeeper.properties &

# Start Kafka Broker
bin/kafka-server-start.sh config/server.properties &

# Start Redis
sudo systemctl start redis-server
```

Verify Kafka is running:
```bash
bin/kafka-topics.sh --bootstrap-server <PSP_VM_IP>:9092 --list
```

### 4.4 Build Services

```bash
cd services/psp-switch
# Build each service individually or use a parent POM
cd tpap-ingress-service && mvn clean package -DskipTests && cd ..
cd transaction-orchestrator && mvn clean package -DskipTests && cd ..
cd npci-adapter && mvn clean package -DskipTests && cd ..
cd npci-response-consumer && mvn clean package -DskipTests && cd ..
cd rules-validation-service && mvn clean package -DskipTests && cd ..
cd ledger-service && mvn clean package -DskipTests && cd ..
cd psp-ledger-service && mvn clean package -DskipTests && cd ..
cd audit-service && mvn clean package -DskipTests && cd ..
cd tpap-egress-service && mvn clean package -DskipTests && cd ..
```

### 4.5 Configuration

Each service reads from its own `application.yml` located in `src/main/resources/`. The key properties that must be set for VM deployment:

| Property | Value |
|---|---|
| `spring.datasource.url` | `jdbc:postgresql://<PSP_VM_IP>:5432/apexupi` |
| `spring.datasource.username` | `apexupi` |
| `spring.datasource.password` | `apexupi` |
| `spring.kafka.bootstrap-servers` | `<PSP_VM_IP>:9092` |
| `spring.data.redis.host` | `<PSP_VM_IP>` |
| `NPCI_BASE_URL` | `http://<NPCI_VM_IP>:9090` |

These can be overridden via environment variables or `--spring.config.location` at startup.

### 4.6 Start Services

Start services in the following order:

```bash
java -jar tpap-ingress-service/target/tpap-ingress-service-*.jar &
java -jar transaction-orchestrator/target/transaction-orchestrator-*.jar &
java -jar rules-validation-service/target/rules-validation-service-*.jar &
java -jar npci-adapter/target/npci-adapter-*.jar &
java -jar npci-response-consumer/target/npci-response-consumer-*.jar &
java -jar ledger-service/target/ledger-service-*.jar &
java -jar psp-ledger-service/target/psp-ledger-service-*.jar &
java -jar audit-service/target/audit-service-*.jar &
java -jar tpap-egress-service/target/tpap-egress-service-*.jar &
```

### 4.7 Port Summary -- VM 1

| Service | Port |
|---|---|
| TPAP Ingress Service | 8080 |
| Transaction Orchestrator | 8081 |
| NPCI Adapter | 8082 |
| NPCI Response Consumer | 8083 |
| TPAP Egress Service | 8084 |
| Rules Validation Service | 8085 |
| Ledger Service | 8086 |
| PSP Ledger Service | 8087 |
| Audit Service | 8088 |
| Kafka Broker | 9092 |
| ZooKeeper | 2181 |
| Redis | 6379 |
| PostgreSQL | 5432 |

---

## 5. VM 2 -- Banking Switch

### 5.1 Prerequisites

| Software | Version |
|---|---|
| Java JDK | 17+ |
| PostgreSQL | 14+ |
| Maven | 3.8+ |

### 5.2 Database Setup

Run the provided setup script:

```bash
cd services/banking-switch/setup
sudo -u postgres psql -f create-db.sql
```

This creates the `bankswitch_db` database with the `transactions` and `event_log` tables. Additionally create the `bank_ledger` table:

```sql
\c bankswitch_db

CREATE TABLE bank_ledger (
    id          SERIAL PRIMARY KEY,
    txn_id      VARCHAR(255),
    account     VARCHAR(255),
    entry_type  VARCHAR(50),
    amount      DOUBLE PRECISION,
    status      VARCHAR(50)
);
```

### 5.3 Build Services

Using the provided install script:

```bash
cd services/banking-switch/setup
chmod +x install.sh
./install.sh
```

Or on Windows (PowerShell):

```powershell
cd services\banking-switch\setup
.\install.ps1
```

This runs `mvn clean install -DskipTests` for all Banking Switch services.

### 5.4 Configuration

Key properties to configure on VM 2:

| Property | Value | Used By |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://<BANK_VM_IP>:5432/bankswitch_db` | Orchestrator, Bank Ledger Service |
| `spring.kafka.bootstrap-servers` | `<PSP_VM_IP>:9092` | All Banking Switch services |
| `cbs.host` | `http://<CBS_VM_IP>:9090` | CBS Adapter |
| `npci.callback-url` | `http://<NPCI_VM_IP>:9090` | NPCI Response Adapter |

Note: Kafka runs on VM 1 (PSP Switch). All Banking Switch services connect to the Kafka broker on VM 1 over the VirtualBox network.

### 5.5 Start Services

Using the provided start script:

```bash
cd services/banking-switch/setup
chmod +x start-all.sh
./start-all.sh
```

Or on Windows (PowerShell):

```powershell
cd services\banking-switch\setup
.\start-all.ps1
```

The scripts start the following services in the background:

1. NPCI Request Listener (port 8080)
2. Transaction Orchestrator (port 8081)
3. CBS Adapter (port 8082)
4. NPCI Response Adapter (port 8083)

The Bank Ledger Service must be started separately:

```bash
java -jar services/banking-switch/bank-ledger-service/target/bank-ledger-service-*.jar &
```

### 5.6 Port Summary -- VM 2

| Service | Port |
|---|---|
| NPCI Request Listener | 8080 |
| Transaction Orchestrator (Banking) | 8081 |
| CBS Adapter | 8082 |
| NPCI Response Adapter | 8083 |
| Bank Ledger Service | 8084 |
| PostgreSQL | 5432 |

---

## 6. VM 3 -- Core Banking System

### 6.1 Prerequisites

| Software | Version |
|---|---|
| Java JDK | 17+ |
| PostgreSQL | 14+ |
| Maven | 3.8+ |

### 6.2 Database Setup

```bash
cd services/cbs/setup
sudo -u postgres psql -f create-db.sql
```

This creates the `cbs_db` database. Then create the tables:

```sql
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

### 6.3 Seed Data

Insert test accounts so that the system can process transactions:

```sql
\c cbs_db

INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('user@bankdemo', 'ACC100001', 50000.00, 'ACTIVE', '<pin_hash>', 0);

INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('payee@bankdemo', 'ACC100002', 10000.00, 'ACTIVE', '<pin_hash>', 0);

INSERT INTO user_credentials (vpa, mpin_hash, device_id)
VALUES ('user@bankdemo', '<mpin_hash>', 'device-001');

INSERT INTO user_credentials (vpa, mpin_hash, device_id)
VALUES ('payee@bankdemo', '<mpin_hash>', 'device-002');
```

### 6.4 Build

```bash
cd services/cbs
mvn clean package -DskipTests
```

Or using the install script:

```bash
cd services/cbs/setup
chmod +x install.sh
./install.sh
```

### 6.5 Configuration

Key properties:

| Property | Value |
|---|---|
| `server.port` | `9090` |
| `spring.datasource.url` | `jdbc:postgresql://<CBS_VM_IP>:5432/cbs_db` |
| `spring.datasource.username` | `postgres` |
| `spring.datasource.password` | (set per environment) |

### 6.6 Start

```bash
java -jar services/cbs/target/cbs-service-0.0.1-SNAPSHOT.jar
```

### 6.7 Verification

From the Banking Switch VM (VM 2):

```bash
curl http://<CBS_VM_IP>:9090/cbs/balance/user@bankdemo
```

Expected:
```json
{"vpa":"user@bankdemo","balance":50000.0000,"status":"SUCCESS"}
```

### 6.8 Port Summary -- VM 3

| Service | Port |
|---|---|
| CBS Service | 9090 |
| PostgreSQL | 5432 |

---

## 7. VM 4 -- NPCI

### 7.1 Prerequisites

| Software | Version |
|---|---|
| Java JDK | 17+ |
| PostgreSQL | 14+ |
| Maven | 3.8+ |

### 7.2 Database Setup

```sql
CREATE DATABASE npci_db;
CREATE USER postgres WITH PASSWORD 'postgres';
```

Table schemas are auto-created by JPA (`ddl-auto: update`) on first startup. The NPCI service manages the following tables:

| Table | Purpose |
|---|---|
| `bank_endpoint` | Maps bank handles to their Banking Switch HTTP endpoint URLs |
| `npci_transaction_log` | Logs all UPI messages routed through NPCI |
| `vpa_registry_entry` | VPA-to-bank discovery registry |

### 7.3 Build

```bash
cd services/npci
mvn clean package -DskipTests
```

### 7.4 Configuration

The NPCI service `application.yml` contains the following configurable properties:

| Property | Value | Description |
|---|---|---|
| `server.port` | `9090` | NPCI service HTTP port |
| `DB_URL` | `jdbc:postgresql://<NPCI_VM_IP>:5432/npci_db` | NPCI PostgreSQL URL |
| `DB_USER` | `postgres` | PostgreSQL username |
| `DB_PASS` | `postgres` | PostgreSQL password |
| `BANKSWITCH_HOST` | `http://<BANK_VM_IP>:8080` | Banking Switch NPCI Request Listener URL |
| `PSP_CALLBACK_URL` | `http://<PSP_VM_IP>:8084` | PSP Switch egress/callback URL |

### 7.5 Start

```bash
java -jar services/npci/target/npci-service-*.jar \
  --DB_URL=jdbc:postgresql://<NPCI_VM_IP>:5432/npci_db \
  --BANKSWITCH_HOST=http://<BANK_VM_IP>:8080 \
  --PSP_CALLBACK_URL=http://<PSP_VM_IP>:8084
```

### 7.6 Port Summary -- VM 4

| Service | Port |
|---|---|
| NPCI Service | 9090 |
| PostgreSQL | 5432 |

---

## 8. End-to-End Verification

After all four VMs are running, verify the full transaction flow:

### Step 1 -- Verify NPCI connectivity to Banking Switch

From VM 4 (NPCI), confirm the Banking Switch is reachable:

```bash
curl -s -o /dev/null -w "%{http_code}" http://<BANK_VM_IP>:8080/bank/upi/ReqBalEnq
```

A `405 Method Not Allowed` response confirms the endpoint is live (GET is not accepted; POST is required).

### Step 2 -- Verify CBS connectivity from Banking Switch

From VM 2 (Banking Switch), confirm CBS is reachable:

```bash
curl http://<CBS_VM_IP>:9090/cbs/balance/user@bankdemo
```

### Step 3 -- Verify Kafka connectivity from Banking Switch

From VM 2 (Banking Switch), confirm the Kafka broker on VM 1 is reachable:

```bash
# From Banking Switch VM, test TCP connectivity to Kafka
nc -zv <PSP_VM_IP> 9092
```

### Step 4 -- Send a test transaction from PSP Switch

From VM 1 (PSP Switch) or an external client, send a test payment request to the TPAP Ingress Service:

```bash
curl -X POST http://<PSP_VM_IP>:8080/upi/pay \
  -H "Content-Type: application/json" \
  -d '{"payerVpa":"user@bankdemo","payeeVpa":"payee@bankdemo","amount":100.00}'
```

Monitor the logs on each VM to trace the request through the full PSP Switch -> NPCI -> Banking Switch -> CBS -> Banking Switch -> NPCI -> PSP Switch cycle.

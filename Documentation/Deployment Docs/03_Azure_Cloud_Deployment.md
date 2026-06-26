# Azure Cloud Deployment

## 1. Overview

This guide covers the deployment of APEX-UPI on three Microsoft Azure virtual machines. The Banking Switch and Core Banking System services are consolidated onto a single VM, reducing the total VM count from four (VirtualBox) to three while keeping the same service set.

---

## 2. VM Specifications

| VM | Name | Azure VM Size (recommended) | OS | vCPUs | RAM | Disk |
|---|---|---|---|---|---|---|
| VM 1 | PSP Switch | Standard_D4s_v5 | Ubuntu 22.04 LTS | 4 | 16 GB | 64 GB |
| VM 2 | NPCI Network | Standard_D2s_v5 | Ubuntu 22.04 LTS | 2 | 8 GB | 32 GB |
| VM 3 | Banking Services | Standard_D4s_v5 | Ubuntu 22.04 LTS | 4 | 16 GB | 64 GB |

VM 1 and VM 3 require more resources because they each host multiple services, a Kafka broker (VM 1 only), and PostgreSQL databases.

---

## 3. Azure Networking

### 3.1 Virtual Network (VNet)

All three VMs must be deployed within the same Azure Virtual Network (VNet) to enable private IP communication without traversing the public internet.

| Configuration | Value |
|---|---|
| VNet Name | `apex-upi-vnet` |
| Address Space | `10.0.0.0/16` |
| Subnet Name | `apex-upi-subnet` |
| Subnet Range | `10.0.1.0/24` |

Each VM receives a private IP from this subnet. All inter-VM communication uses these private IPs.

### 3.2 Network Security Group (NSG) Rules

The following inbound rules must be configured on the NSG attached to each VM's network interface. All rules apply to the VNet source address space (`10.0.0.0/16`).

#### VM 1 -- PSP Switch

| Priority | Name | Port | Protocol | Source | Action |
|---|---|---|---|---|---|
| 100 | AllowSSH | 22 | TCP | Any | Allow |
| 110 | AllowTPAPIngress | 8080 | TCP | VNet | Allow |
| 120 | AllowKafka | 9092 | TCP | VNet | Allow |
| 130 | AllowZooKeeper | 2181 | TCP | VNet | Allow |
| 140 | AllowRedis | 6379 | TCP | VNet | Allow |
| 150 | AllowPostgreSQL | 5432 | TCP | VNet | Allow |
| 160 | AllowPSPServices | 8081-8088 | TCP | VNet | Allow |

#### VM 2 -- NPCI Network

| Priority | Name | Port | Protocol | Source | Action |
|---|---|---|---|---|---|
| 100 | AllowSSH | 22 | TCP | Any | Allow |
| 110 | AllowNPCIService | 9090 | TCP | VNet | Allow |
| 120 | AllowPostgreSQL | 5432 | TCP | VNet | Allow |

#### VM 3 -- Banking Services

| Priority | Name | Port | Protocol | Source | Action |
|---|---|---|---|---|---|
| 100 | AllowSSH | 22 | TCP | Any | Allow |
| 110 | AllowBankingServices | 8080-8084 | TCP | VNet | Allow |
| 120 | AllowCBSService | 9090 | TCP | VNet | Allow |
| 130 | AllowPostgreSQL | 5432 | TCP | VNet | Allow |

---

## 4. VM 1 -- PSP Switch

The setup for VM 1 is identical to the VirtualBox local deployment (VM 1). Refer to [02_VirtualBox_Local_Deployment.md](./02_VirtualBox_Local_Deployment.md), Section 4, with the following address substitutions:

| Placeholder | Azure Value |
|---|---|
| `<PSP_VM_IP>` | Private IP of VM 1 (e.g., `10.0.1.4`) |
| `<NPCI_VM_IP>` | Private IP of VM 2 (e.g., `10.0.1.5`) |

All other steps (database setup, Kafka/Redis/ZooKeeper start, build, service start) are the same.

---

## 5. VM 2 -- NPCI Network

The setup for VM 2 is identical to the VirtualBox local deployment (VM 4). Refer to [02_VirtualBox_Local_Deployment.md](./02_VirtualBox_Local_Deployment.md), Section 7, with the following address substitutions:

| Property | Azure Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://10.0.1.5:5432/npci_db` (VM 2 private IP) |
| `BANKSWITCH_HOST` | `http://10.0.1.6:8080` (VM 3 private IP -- Banking Services) |
| `PSP_CALLBACK_URL` | `http://10.0.1.4:8084` (VM 1 private IP -- PSP Switch) |

---

## 6. VM 3 -- Banking Services (Banking Switch + CBS)

This VM hosts both the Banking Switch microservices and the CBS service. This is the key difference from the VirtualBox layout.

### 6.1 Prerequisites

| Software | Version |
|---|---|
| Java JDK | 17+ |
| PostgreSQL | 14+ |
| Maven | 3.8+ |

### 6.2 Database Setup

VM 3 hosts two PostgreSQL databases on a single PostgreSQL instance:

```bash
sudo -u postgres psql
```

```sql
-- Banking Switch database
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

-- CBS database
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

### 6.3 Seed Data (CBS)

```sql
\c cbs_db

INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('user@bankdemo', 'ACC100001', 50000.00, 'ACTIVE', '<pin_hash>', 0);

INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('payee@bankdemo', 'ACC100002', 10000.00, 'ACTIVE', '<pin_hash>', 0);
```

### 6.4 Build All Services

```bash
# Build CBS
cd services/cbs
mvn clean package -DskipTests

# Build Banking Switch
cd services/banking-switch/setup
chmod +x install.sh
./install.sh
```

### 6.5 Configuration -- Key Difference from Local

On this VM, the CBS Adapter communicates with the CBS service over the same machine. The `cbs.host` property points to the internal address of VM 3 rather than a separate VM.

| Property | Value | Notes |
|---|---|---|
| `cbs.host` | `http://<VM3_PRIVATE_IP>:9090` | CBS on same VM |
| `spring.kafka.bootstrap-servers` | `<VM1_PRIVATE_IP>:9092` | Kafka on VM 1 |
| `npci.callback-url` | `http://<VM2_PRIVATE_IP>:9090` | NPCI on VM 2 |
| `spring.datasource.url` (Banking Switch services) | `jdbc:postgresql://<VM3_PRIVATE_IP>:5432/bankswitch_db` | Local PostgreSQL |
| `spring.datasource.url` (CBS) | `jdbc:postgresql://<VM3_PRIVATE_IP>:5432/cbs_db` | Local PostgreSQL |

### 6.6 Startup Order

Start services in this order on VM 3:

1. **CBS Service** (must be running before CBS Adapter processes any events)

```bash
java -jar services/cbs/target/cbs-service-0.0.1-SNAPSHOT.jar &
```

2. **Banking Switch services**

```bash
java -jar services/banking-switch/npci-request-listener/target/npci-request-listener-*.jar &
java -jar services/banking-switch/orchestrator/target/orchestrator-*.jar &
java -jar services/banking-switch/cbs-adapter/target/cbs-adapter-*.jar &
java -jar services/banking-switch/npci-response-adapter/target/npci-response-adapter-*.jar &
java -jar services/banking-switch/bank-ledger-service/target/bank-ledger-service-*.jar &
```

### 6.7 Port Summary -- VM 3

| Service | Port |
|---|---|
| NPCI Request Listener | 8080 |
| Transaction Orchestrator (Banking) | 8081 |
| CBS Adapter | 8082 |
| NPCI Response Adapter | 8083 |
| Bank Ledger Service | 8084 |
| CBS Service | 9090 |
| PostgreSQL (`bankswitch_db` + `cbs_db`) | 5432 |

---

## 7. Azure Startup Order -- All VMs

| Step | VM | Action |
|---|---|---|
| 1 | VM 1 | Start PostgreSQL, ZooKeeper, Kafka, Redis |
| 2 | VM 2 | Start PostgreSQL |
| 3 | VM 3 | Start PostgreSQL |
| 4 | VM 2 | Start NPCI Service |
| 5 | VM 3 | Start CBS Service |
| 6 | VM 3 | Start Banking Switch services (all five) |
| 7 | VM 1 | Start PSP Switch services (all nine) |

---

## 8. Differences from Local (VirtualBox) Deployment

| Aspect | VirtualBox (4 VMs) | Azure (3 VMs) |
|---|---|---|
| VM count | 4 | 3 |
| CBS hosting | Dedicated VM (VM 3) | Shared with Banking Switch (VM 3) |
| NPCI hosting | Dedicated VM (VM 4) | Dedicated VM (VM 2) |
| `cbs.host` in CBS Adapter | Points to CBS VM IP | Points to same VM internal IP |
| PostgreSQL instances on VM 3 | 1 database (`cbs_db`) | 2 databases (`bankswitch_db` + `cbs_db`) |
| Network | VirtualBox Host-Only or Bridged | Azure VNet with private IPs |
| Firewall | OS-level (`ufw` / Windows Firewall) | Azure NSG rules |

---

## 9. Azure-Specific Verification

After all three VMs are started, run the following checks:

### From VM 3 (Banking Services) -- Verify CBS is up

```bash
curl http://<VM3_PRIVATE_IP>:9090/cbs/balance/user@bankdemo
```

### From VM 3 -- Verify Kafka connectivity to VM 1

```bash
nc -zv <VM1_PRIVATE_IP> 9092
```

### From VM 2 (NPCI) -- Verify Banking Switch is reachable

```bash
curl -X POST http://<VM3_PRIVATE_IP>:8080/bank/upi/ReqBalEnq \
  -H "Content-Type: application/xml" \
  -d '<ReqBalEnq><Txn id="test-001"><Payer addr="user@bankdemo"/></Txn></ReqBalEnq>'
```

### From VM 1 (PSP Switch) -- Verify NPCI is reachable

```bash
curl -s -o /dev/null -w "%{http_code}" http://<VM2_PRIVATE_IP>:9090
```

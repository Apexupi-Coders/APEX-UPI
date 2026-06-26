# Database Setup

## 1. Overview

The APEX-UPI platform uses PostgreSQL across all VMs. Each subsystem maintains its own database. This document consolidates all database creation scripts, table DDL, and seed data in a single reference for both VirtualBox and Azure deployments.

---

## 2. Database Inventory

### Local Deployment (VirtualBox -- 4 VMs)

| VM | Database | Owner Subsystem | PostgreSQL Port |
|---|---|---|---|
| VM 1 -- PSP Switch | `apexupi` | PSP Switch | 5432 |
| VM 2 -- Banking Switch | `bankswitch_db` | Banking Switch | 5432 |
| VM 3 -- CBS | `cbs_db` | Core Banking System | 5432 |
| VM 4 -- NPCI | `npci_db` | NPCI Network | 5432 |

### Cloud Deployment (Azure -- 3 VMs)

| VM | Databases | Owner Subsystem | PostgreSQL Port |
|---|---|---|---|
| VM 1 -- PSP Switch | `apexupi` | PSP Switch | 5432 |
| VM 2 -- NPCI Network | `npci_db` | NPCI Network | 5432 |
| VM 3 -- Banking Services | `bankswitch_db`, `cbs_db` | Banking Switch, CBS | 5432 |

In the Azure deployment, VM 3 hosts two databases on a single PostgreSQL instance.

---

## 3. PostgreSQL Installation

### Ubuntu 22.04

```bash
sudo apt update
sudo apt install postgresql postgresql-contrib -y
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### Verify PostgreSQL is running

```bash
sudo -u postgres psql -c "SELECT version();"
```

### Allow remote connections (required for cross-VM access)

Edit `postgresql.conf`:
```
listen_addresses = '*'
```

Edit `pg_hba.conf` to allow connections from the VNet subnet:
```
host    all    all    10.0.0.0/16    md5
```

For VirtualBox, replace the subnet with the appropriate host-only or bridged network range (e.g., `192.168.56.0/24`).

Restart PostgreSQL after configuration changes:
```bash
sudo systemctl restart postgresql
```

---

## 4. Database: `apexupi` (VM 1 -- PSP Switch)

### Creation

```sql
CREATE DATABASE apexupi;
CREATE USER apexupi WITH PASSWORD 'apexupi';
GRANT ALL PRIVILEGES ON DATABASE apexupi TO apexupi;
```

### Tables

Table schemas for the PSP Switch are auto-created by Spring Data JPA on first service startup (`ddl-auto: update`). No manual DDL is required.

The following tables are created automatically:

| Table | Created By Service | Purpose |
|---|---|---|
| `transactions` | Transaction Orchestrator (PSP) | PSP-side transaction state tracking |
| `event_log` | Transaction Orchestrator (PSP) | PSP-side event history |
| `ledger_entries` | Ledger Service | PSP-side financial ledger |
| `psp_ledger_entries` | PSP Ledger Service | PSP-specific ledger records |
| `audit_entries` | Audit Service | Audit trail for compliance |

---

## 5. Database: `bankswitch_db` (VM 2 / VM 3)

### Creation

```sql
CREATE DATABASE bankswitch_db;
```

Setup script: `services/banking-switch/setup/create-db.sql`

### Tables

```sql
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

### Table Descriptions

| Table | Owner Service | Purpose |
|---|---|---|
| `transactions` | Transaction Orchestrator (Banking) | Tracks lifecycle state of each inbound UPI transaction |
| `event_log` | Transaction Orchestrator (Banking) | Append-only event history for every state transition |
| `bank_ledger` | Bank Ledger Service | Confirmed debit/credit entries for bank-side accounting |

---

## 6. Database: `cbs_db` (VM 3)

### Creation

```sql
CREATE DATABASE cbs_db;
```

Setup script: `services/cbs/setup/create-db.sql`

### Tables

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

### Table Descriptions

| Table | Owner Service | Purpose |
|---|---|---|
| `accounts` | CBS Service | Account balances, VPA mapping, status, hashed PIN, optimistic lock version |
| `transaction_ledger` | CBS Service (via LedgerService) | Immutable financial movement record for every committed debit/credit |
| `user_credentials` | CBS Service | VPA-to-MPIN hash and device binding for authentication |

### Seed Data

The `accounts` table must be pre-populated with test accounts before transactions can be processed:

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

Replace `<pin_hash>` and `<mpin_hash>` with actual SHA-256 or bcrypt hashed values appropriate for the test environment.

---

## 7. Database: `npci_db` (VM 4 / VM 2)

### Creation

```sql
CREATE DATABASE npci_db;
```

### Tables

Table schemas are auto-created by JPA (`ddl-auto: update`). The following tables are created on first startup:

| Table | Purpose |
|---|---|
| `bank_endpoint` | Maps bank handles (e.g., `@bankdemo`) to their Banking Switch endpoint URLs |
| `npci_transaction_log` | Logs all UPI messages routed through the NPCI simulator |
| `vpa_registry_entry` | VPA-to-bank discovery registry used for routing |

### Seed Data

The NPCI service requires bank endpoint registrations so it knows where to route UPI messages. Insert the banking switch endpoint for the demo bank:

```sql
\c npci_db

INSERT INTO bank_endpoint (bank_handle, endpoint_url, bank_name)
VALUES ('@bankdemo', 'http://<BANK_VM_IP>:8080', 'Demo Bank');

INSERT INTO vpa_registry_entry (vpa, bank_handle)
VALUES ('user@bankdemo', '@bankdemo');

INSERT INTO vpa_registry_entry (vpa, bank_handle)
VALUES ('payee@bankdemo', '@bankdemo');
```

Replace `<BANK_VM_IP>` with the actual IP of VM 2 (VirtualBox) or VM 3 (Azure).

---

## 8. Complete Setup Script -- Azure VM 3

For the Azure deployment, both `bankswitch_db` and `cbs_db` are created on the same PostgreSQL instance. The following consolidated script sets up everything on VM 3:

```bash
sudo -u postgres psql << 'EOF'

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

-- Seed data
INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('user@bankdemo', 'ACC100001', 50000.00, 'ACTIVE', '<pin_hash>', 0);

INSERT INTO accounts (vpa, account_number, balance, status, pin_hash, version)
VALUES ('payee@bankdemo', 'ACC100002', 10000.00, 'ACTIVE', '<pin_hash>', 0);

INSERT INTO user_credentials (vpa, mpin_hash, device_id)
VALUES ('user@bankdemo', '<mpin_hash>', 'device-001');

INSERT INTO user_credentials (vpa, mpin_hash, device_id)
VALUES ('payee@bankdemo', '<mpin_hash>', 'device-002');

EOF
```

---

## 9. Backup and Restore

### Backup a database

```bash
pg_dump -U postgres -d bankswitch_db > bankswitch_db_backup.sql
pg_dump -U postgres -d cbs_db > cbs_db_backup.sql
```

### Restore a database

```bash
psql -U postgres -d bankswitch_db < bankswitch_db_backup.sql
psql -U postgres -d cbs_db < cbs_db_backup.sql
```

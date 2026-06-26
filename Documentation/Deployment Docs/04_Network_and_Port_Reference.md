# Network and Port Reference

## 1. Overview

This document provides a consolidated reference of all network ports, inter-VM communication paths, and Kafka broker addressing for both the VirtualBox (4-VM) and Azure (3-VM) deployment topologies. Use this as a quick-reference for firewall configuration, troubleshooting, and security review.

---

## 2. Port Allocation -- Local Deployment (VirtualBox, 4 VMs)

### VM 1 -- PSP Switch

| Port | Protocol | Service / Component |
|---|---|---|
| 8080 | TCP/HTTP | TPAP Ingress Service |
| 8081 | TCP/HTTP | Transaction Orchestrator (PSP) |
| 8082 | TCP/HTTP | NPCI Adapter |
| 8083 | TCP/HTTP | NPCI Response Consumer |
| 8084 | TCP/HTTP | TPAP Egress Service |
| 8085 | TCP/HTTP | Rules Validation Service |
| 8086 | TCP/HTTP | Ledger Service |
| 8087 | TCP/HTTP | PSP Ledger Service |
| 8088 | TCP/HTTP | Audit Service |
| 9092 | TCP | Kafka Broker (external listener) |
| 29092 | TCP | Kafka Broker (internal listener, used within VM only) |
| 2181 | TCP | ZooKeeper |
| 6379 | TCP | Redis |
| 5432 | TCP | PostgreSQL (`apexupi`) |

### VM 2 -- Banking Switch

| Port | Protocol | Service / Component |
|---|---|---|
| 8080 | TCP/HTTP | NPCI Request Listener |
| 8081 | TCP/HTTP | Transaction Orchestrator (Banking) |
| 8082 | TCP/HTTP | CBS Adapter |
| 8083 | TCP/HTTP | NPCI Response Adapter |
| 8084 | TCP/HTTP | Bank Ledger Service |
| 5432 | TCP | PostgreSQL (`bankswitch_db`) |

### VM 3 -- Core Banking System

| Port | Protocol | Service / Component |
|---|---|---|
| 9090 | TCP/HTTP | CBS Service |
| 5432 | TCP | PostgreSQL (`cbs_db`) |

### VM 4 -- NPCI

| Port | Protocol | Service / Component |
|---|---|---|
| 9090 | TCP/HTTP | NPCI Service |
| 5432 | TCP | PostgreSQL (`npci_db`) |

---

## 3. Port Allocation -- Cloud Deployment (Azure, 3 VMs)

### VM 1 -- PSP Switch

Same as VirtualBox VM 1 (see Section 2).

### VM 2 -- NPCI Network

Same as VirtualBox VM 4 (see Section 2).

### VM 3 -- Banking Services (Banking Switch + CBS)

| Port | Protocol | Service / Component |
|---|---|---|
| 8080 | TCP/HTTP | NPCI Request Listener |
| 8081 | TCP/HTTP | Transaction Orchestrator (Banking) |
| 8082 | TCP/HTTP | CBS Adapter |
| 8083 | TCP/HTTP | NPCI Response Adapter |
| 8084 | TCP/HTTP | Bank Ledger Service |
| 9090 | TCP/HTTP | CBS Service |
| 5432 | TCP | PostgreSQL (`bankswitch_db` + `cbs_db`) |

---

## 4. Inter-VM Communication Matrix -- Local (VirtualBox)

This table lists every network call that crosses a VM boundary. All calls are TCP-based HTTP requests.

| Source VM | Target VM | Source Service | Target Service | Target Port | Protocol | Payload Format |
|---|---|---|---|---|---|---|
| VM 1 (PSP) | VM 4 (NPCI) | NPCI Adapter | NPCI Service | 9090 | HTTP POST | UPI XML |
| VM 4 (NPCI) | VM 1 (PSP) | NPCI Service | TPAP Egress Service | 8084 | HTTP POST | UPI XML |
| VM 4 (NPCI) | VM 2 (Banking) | NPCI Service | NPCI Request Listener | 8080 | HTTP POST | UPI XML |
| VM 2 (Banking) | VM 4 (NPCI) | NPCI Response Adapter | NPCI Service | 9090 | HTTP POST | UPI XML |
| VM 2 (Banking) | VM 3 (CBS) | CBS Adapter | CBS Service | 9090 | HTTP GET/POST | JSON |
| VM 2 (Banking) | VM 1 (PSP) | All Banking Switch services | Kafka Broker | 9092 | Kafka protocol | Binary |

---

## 5. Inter-VM Communication Matrix -- Cloud (Azure)

| Source VM | Target VM | Source Service | Target Service | Target Port | Protocol | Payload Format |
|---|---|---|---|---|---|---|
| VM 1 (PSP) | VM 2 (NPCI) | NPCI Adapter | NPCI Service | 9090 | HTTP POST | UPI XML |
| VM 2 (NPCI) | VM 1 (PSP) | NPCI Service | TPAP Egress Service | 8084 | HTTP POST | UPI XML |
| VM 2 (NPCI) | VM 3 (Banking) | NPCI Service | NPCI Request Listener | 8080 | HTTP POST | UPI XML |
| VM 3 (Banking) | VM 2 (NPCI) | NPCI Response Adapter | NPCI Service | 9090 | HTTP POST | UPI XML |
| VM 3 (Banking) | VM 3 (Banking) | CBS Adapter | CBS Service | 9090 | HTTP GET/POST | JSON (internal) |
| VM 3 (Banking) | VM 1 (PSP) | All Banking Switch services | Kafka Broker | 9092 | Kafka protocol | Binary |

Note: In Azure, the CBS Adapter-to-CBS call remains on VM 3. It does not cross a VM boundary but is listed for completeness.

---

## 6. Kafka Broker Addressing

The Kafka broker runs exclusively on VM 1 (PSP Switch) in both deployment topologies.

### Broker Configuration

| Listener | Bind Address | Advertised Address | Purpose |
|---|---|---|---|
| `INTERNAL` | `0.0.0.0:29092` | `kafka:29092` (or `<PSP_VM_IP>:29092`) | Used by services on VM 1 |
| `EXTERNAL` | `0.0.0.0:9092` | `<PSP_VM_IP>:9092` | Used by services on all other VMs |

### Client Configuration

Services on VM 1 (PSP Switch) should use:
```yaml
spring.kafka.bootstrap-servers: <PSP_VM_IP>:9092
```

Services on VM 2, VM 3, VM 4 (all other VMs) must use:
```yaml
spring.kafka.bootstrap-servers: <PSP_VM_IP>:9092
```

The Kafka `advertised.listeners` configuration on the broker must advertise the external IP of VM 1. If it advertises `localhost`, remote consumers will fail to connect after initial metadata fetch.

---

## 7. Kafka Topics (All Subsystems)

| Topic | Producing VM | Consuming VM |
|---|---|---|
| `tpap.inbound.request` | VM 1 | VM 1 |
| `npci.outbound.request` | VM 1 | VM 1 |
| `npci.inbound.response` | VM 1 | VM 1 |
| `tpap.outbound.response` | VM 1 | VM 1 |
| `banking.inbound.txn` | VM 2 / VM 3 | VM 2 / VM 3 |
| `banking.cbs.request` | VM 2 / VM 3 | VM 2 / VM 3 |
| `banking.cbs.response` | VM 2 / VM 3 | VM 2 / VM 3 |
| `banking.npci.response` | VM 2 / VM 3 | VM 2 / VM 3 |
| `upi.cbs.debit.confirm` | External | VM 2 / VM 3 |
| `upi.cbs.credit.confirm` | External | VM 2 / VM 3 |

All topics are auto-created by Kafka (`auto.create.topics.enable: true`).

---

## 8. Firewall Configuration

### Linux (UFW)

For each VM, open only the ports listed in the port allocation tables above. Example for VM 2 (Banking Switch):

```bash
sudo ufw allow 22/tcp           # SSH
sudo ufw allow 8080:8084/tcp    # Banking Switch services
sudo ufw allow 5432/tcp         # PostgreSQL (restrict source to VNet range)
sudo ufw enable
```

### Windows Firewall

For Windows-based VMs, create inbound rules for each port:

```powershell
New-NetFirewallRule -DisplayName "Banking-NPCI-Listener" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
New-NetFirewallRule -DisplayName "Banking-Orchestrator" -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow
New-NetFirewallRule -DisplayName "Banking-CBS-Adapter" -Direction Inbound -Protocol TCP -LocalPort 8082 -Action Allow
New-NetFirewallRule -DisplayName "Banking-NPCI-Response" -Direction Inbound -Protocol TCP -LocalPort 8083 -Action Allow
New-NetFirewallRule -DisplayName "Banking-Ledger" -Direction Inbound -Protocol TCP -LocalPort 8084 -Action Allow
New-NetFirewallRule -DisplayName "PostgreSQL" -Direction Inbound -Protocol TCP -LocalPort 5432 -Action Allow
```

---

## 9. Troubleshooting Connectivity

| Symptom | Likely Cause | Resolution |
|---|---|---|
| Kafka consumer logs `Connection refused` to port 9092 | Kafka not started, or firewall blocking 9092 on VM 1 | Verify Kafka broker is running on VM 1. Open port 9092 in firewall. |
| Kafka consumer connects but immediately disconnects | `advertised.listeners` in Kafka is set to `localhost` instead of the external IP | Update `server.properties`: `advertised.listeners=PLAINTEXT://<PSP_VM_IP>:9092` |
| CBS Adapter logs `CBS_UNAVAILABLE` | CBS service on VM 3 is not running or port 9090 is blocked | Start CBS service. Verify firewall allows 9090 on VM 3. |
| NPCI Response Adapter logs `Failed to send callback` | NPCI service not running or port 9090 blocked on NPCI VM | Start NPCI service. Verify firewall on NPCI VM. |
| Banking Switch receives no inbound events | NPCI `BANKSWITCH_HOST` property points to wrong IP | Verify NPCI config: `BANKSWITCH_HOST` must be `http://<BANK_VM_IP>:8080` |

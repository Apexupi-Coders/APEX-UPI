# Deployment Overview

## 1. Introduction

The APEX-UPI platform is deployed as a set of independent Java processes across multiple virtual machines. Each VM hosts a distinct logical subsystem. No containerization (Docker, Kubernetes, etc.) is used at any layer -- all services run as standalone Java JAR processes directly on each VM's operating system.

This document defines the two supported deployment topologies: a four-VM local layout using Oracle VirtualBox and a three-VM cloud layout using Microsoft Azure.

---

## 2. System Components

The APEX-UPI platform consists of four logical subsystems. Each subsystem runs its own set of services, databases, and infrastructure:

| Subsystem | Role | Services Count |
|---|---|---|
| PSP Switch | Payment Service Provider gateway. Receives TPAP requests, orchestrates UPI transactions, communicates with NPCI, manages ledger, audit, and egress. | 9 microservices |
| NPCI Network | National Payments Corporation of India simulator. Routes UPI messages between PSP Switch and Banking Switch. Maintains VPA registry and bank endpoint registry. | 1 service |
| Banking Switch | Bank-side transaction processor. Receives inbound UPI requests from NPCI, coordinates with CBS, returns results. | 5 microservices |
| Core Banking System (CBS) | Account authority. Manages account balances, processes debit/credit with transactional integrity, maintains transaction ledger. | 1 service |

---

## 3. Local Deployment -- VirtualBox (4 VMs)

In local deployment, each logical subsystem runs on its own dedicated VirtualBox virtual machine. This provides complete isolation between subsystems and mirrors a production-like separation of concern.

### VM Allocation

| VM | Name | Subsystem | Hosted Services |
|---|---|---|---|
| VM 1 | PSP Switch | PSP Switch | TPAP Ingress Service, Transaction Orchestrator, NPCI Adapter, NPCI Response Consumer, Rules Validation Service, Ledger Service, PSP Ledger Service, Audit Service, TPAP Egress Service, Apache Kafka (ZooKeeper + Broker), Redis, PostgreSQL (`apexupi` database) |
| VM 2 | Banking Switch | Banking Switch | NPCI Request Listener, Transaction Orchestrator (Banking), CBS Adapter, NPCI Response Adapter, Bank Ledger Service, PostgreSQL (`bankswitch_db` database) |
| VM 3 | Core Banking System | CBS | CBS Service, PostgreSQL (`cbs_db` database) |
| VM 4 | NPCI | NPCI Network | NPCI Service, PostgreSQL (`npci_db` database) |

### Communication Flow

```
VM 1 (PSP Switch)
    |
    |  HTTP (UPI XML) via NPCI Adapter
    v
VM 4 (NPCI)
    |
    |  HTTP (UPI XML) routed to Banking Switch
    v
VM 2 (Banking Switch)
    |
    |  HTTP REST (JSON) via CBS Adapter
    v
VM 3 (CBS)
```

Each arrow represents an HTTP call between VMs. Internal communication within each VM uses Kafka topics (on VM 1, shared by its services) and direct method calls.

---

## 4. Cloud Deployment -- Azure (3 VMs)

In the Azure deployment, the Banking Switch and CBS are consolidated onto a single VM. This reduces the VM count from four to three while retaining the same service set.

### VM Allocation

| VM | Name | Subsystem | Hosted Services |
|---|---|---|---|
| VM 1 | PSP Switch | PSP Switch | TPAP Ingress Service, Transaction Orchestrator, NPCI Adapter, NPCI Response Consumer, Rules Validation Service, Ledger Service, PSP Ledger Service, Audit Service, TPAP Egress Service, Apache Kafka (ZooKeeper + Broker), Redis, PostgreSQL (`apexupi` database) |
| VM 2 | NPCI Network | NPCI Network | NPCI Service, PostgreSQL (`npci_db` database) |
| VM 3 | Banking Services | Banking Switch + CBS | NPCI Request Listener, Transaction Orchestrator (Banking), CBS Adapter, NPCI Response Adapter, Bank Ledger Service, CBS Service, PostgreSQL (`bankswitch_db` + `cbs_db` databases) |

### Communication Flow

```
VM 1 (PSP Switch)
    |
    |  HTTP (UPI XML)
    v
VM 2 (NPCI Network)
    |
    |  HTTP (UPI XML)
    v
VM 3 (Banking Services)
    |
    |  CBS Adapter calls CBS service on the same VM (internal network)
    v
    CBS Service (same VM 3)
```

The key difference from local deployment: the CBS Adapter on VM 3 communicates with the CBS Service on the same VM rather than making a cross-VM call. The `cbs.host` configuration property in the CBS Adapter points to the internal address of VM 3.

---

## 5. Infrastructure Dependencies Per VM

### Local (VirtualBox)

| VM | Java | PostgreSQL | Kafka | Redis |
|---|---|---|---|---|
| VM 1 -- PSP Switch | 17 | Yes (`apexupi`) | Yes (Broker + ZooKeeper) | Yes |
| VM 2 -- Banking Switch | 17 | Yes (`bankswitch_db`) | No (connects to VM 1) | No |
| VM 3 -- CBS | 17 | Yes (`cbs_db`) | No | No |
| VM 4 -- NPCI | 17 | Yes (`npci_db`) | No | No |

### Cloud (Azure)

| VM | Java | PostgreSQL | Kafka | Redis |
|---|---|---|---|---|
| VM 1 -- PSP Switch | 17 | Yes (`apexupi`) | Yes (Broker + ZooKeeper) | Yes |
| VM 2 -- NPCI Network | 17 | Yes (`npci_db`) | No | No |
| VM 3 -- Banking Services | 17 | Yes (`bankswitch_db`, `cbs_db`) | No (connects to VM 1) | No |

---

## 6. Global Prerequisites

Every VM requires the following installed before any services can be started:

| Software | Version | Purpose |
|---|---|---|
| Java (JDK) | 17+ | Runtime for all Spring Boot services |
| PostgreSQL | 14+ | Database for service state and account data |
| Apache Maven | 3.8+ | Build-time only; used to compile and package JAR files |

Additionally, VM 1 (PSP Switch) requires:

| Software | Version | Purpose |
|---|---|---|
| Apache Kafka | 3.x+ | Message broker for inter-service communication |
| Apache ZooKeeper | 3.8+ | Kafka coordination (bundled with Kafka distribution) |
| Redis | 7+ | Caching and idempotency store for PSP Switch |

---

## 7. Startup Order

Services must be started in the correct order to avoid connection failures on startup. Infrastructure components must be running before application services.

### Infrastructure (start first, in order)

1. PostgreSQL on all VMs
2. Apache ZooKeeper on VM 1 (PSP Switch)
3. Apache Kafka Broker on VM 1 (PSP Switch)
4. Redis on VM 1 (PSP Switch)

### Application Services (start after infrastructure)

1. VM 4 / VM 2 (NPCI) -- NPCI Service
2. VM 3 (CBS) -- CBS Service (Local only; in Azure, start on VM 3 before Banking Switch services)
3. VM 2 / VM 3 (Banking Switch) -- NPCI Request Listener, Transaction Orchestrator, CBS Adapter, NPCI Response Adapter, Bank Ledger Service
4. VM 1 (PSP Switch) -- All PSP Switch services

Within a given VM, the startup order of individual services is documented in the VM-specific deployment guides:
- [02_VirtualBox_Local_Deployment.md](./02_VirtualBox_Local_Deployment.md)
- [03_Azure_Cloud_Deployment.md](./03_Azure_Cloud_Deployment.md)

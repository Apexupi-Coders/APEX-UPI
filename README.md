# APEX-UPI

APEX-UPI is a full-stack, microservices-based UPI (Unified Payments Interface) platform built to simulate and demonstrate the complete payment lifecycle across four core subsystems: PSP Switch, NPCI Network, Banking Switch, and Core Banking System (CBS).

The platform is designed for educational and demonstration purposes and reflects the real-world architecture of a UPI-compliant payment system as per NPCI specifications.

---

## System Architecture

The platform is divided into four independent subsystems. Each subsystem is deployed on its own virtual machine and communicates with adjacent subsystems over HTTP and Kafka.

```
TPAP (Mobile App / Client)
        |
        | HTTP (REST)
        v
  PSP Switch  <------>  NPCI Network
  (VM 1)                (VM 4 / VM 2)
                              |
                              | HTTP (UPI XML)
                              v
                       Banking Switch
                       (VM 2 / VM 3)
                              |
                              | HTTP (REST)
                              v
                    Core Banking System
                       (VM 3 / VM 3)
```

### Subsystems

| Subsystem | Role | Services |
|---|---|---|
| PSP Switch | Receives TPAP payment requests, validates rules, coordinates with NPCI, maintains PSP ledger and audit trail | 9 microservices |
| NPCI Network | Routes UPI messages between PSP and Banking Switch, maintains VPA registry and bank endpoint directory | 1 service |
| Banking Switch | Receives inbound UPI requests from NPCI, orchestrates CBS operations via a saga pattern, returns results | 5 microservices |
| Core Banking System | Manages account balances and executes debit/credit operations with full transactional integrity | 1 service |

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Messaging | Apache Kafka |
| Database | PostgreSQL 14+ |
| Cache | Redis 7 |
| Build | Apache Maven 3.8+ |
| Dashboard | HTML / JavaScript (operations dashboard) |

---

## Repository Structure

```
APEX-UPI/
├── services/
│   ├── psp-switch/              PSP Switch microservices (9 services)
│   ├── banking-switch/          Banking Switch microservices (5 services)
│   ├── cbs/                     Core Banking System
│   └── npci/                    NPCI Network service
├── Documentation/
│   ├── PSP Switch Docs/         Detailed docs for all PSP Switch components
│   ├── Banking Switch Docs/     Detailed docs for all Banking Switch components
│   ├── Core Banking Services Docs/   Detailed docs for CBS components
│   └── Deployment Docs/         VM topology, setup guides, network reference
├── dashboard-ui/                Operations monitoring dashboard
├── integration/                 Integration test configuration
├── docker-compose.cloud.yml     Reference compose file (infrastructure only)
├── Business Requirement Document.pdf
├── High Level Design.pdf
└── Software Requirement Specification.pdf
```

---

## Documentation

All technical documentation is located in the `Documentation/` directory. Each folder contains a `README.md` index file.

| Folder | Contents |
|---|---|
| [PSP Switch Docs](./Documentation/PSP%20Switch%20Docs/README.md) | Architecture overview, all 9 service specs, Kafka event contracts, data models, configuration |
| [Banking Switch Docs](./Documentation/Banking%20Switch%20Docs/README.md) | Architecture overview, all 5 service specs, saga flow, Kafka event contracts, data models, configuration |
| [Core Banking Services Docs](./Documentation/Core%20Banking%20Services%20Docs/README.md) | CBS service overview, Account/Debit/Credit/Ledger service specs, REST API reference, data models |
| [Deployment Docs](./Documentation/Deployment%20Docs/README.md) | VM topology for VirtualBox and Azure, database setup, network and port reference |

---

## Deployment

The platform supports two deployment environments. In both cases all services run as standalone Java processes on virtual machines.

### Local -- VirtualBox (4 VMs)

| VM | Subsystem | Key Services |
|---|---|---|
| VM 1 | PSP Switch | 9 microservices, Kafka, Redis, PostgreSQL (`apexupi`) |
| VM 2 | Banking Switch | 5 microservices, PostgreSQL (`bankswitch_db`) |
| VM 3 | Core Banking System | CBS service, PostgreSQL (`cbs_db`) |
| VM 4 | NPCI Network | NPCI service, PostgreSQL (`npci_db`) |

### Cloud -- Azure (3 VMs)

| VM | Subsystem | Key Services |
|---|---|---|
| VM 1 | PSP Switch | 9 microservices, Kafka, Redis, PostgreSQL (`apexupi`) |
| VM 2 | NPCI Network | NPCI service, PostgreSQL (`npci_db`) |
| VM 3 | Banking Services | Banking Switch + CBS, PostgreSQL (`bankswitch_db`, `cbs_db`) |

For full setup instructions, database scripts, and port reference, see [Deployment Docs](./Documentation/Deployment%20Docs/README.md).

---

## Getting Started

### Prerequisites

- Java 17+
- PostgreSQL 14+
- Apache Kafka 3.x
- Redis 7+ (PSP Switch VM only)
- Apache Maven 3.8+

### Build a service

```bash
cd services/<subsystem>/<service-name>
mvn clean package -DskipTests
```

### Run a service

```bash
java -jar target/<service-name>-*.jar --spring.config.location=<path-to-application.yml>
```

Refer to the deployment documentation for the correct startup order and configuration properties for each VM.

---

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for branch naming conventions, commit message format, and pull request checklist.

---

## Project Documents

- [Business Requirement Document](./Business%20Requirement%20Document.pdf)
- [Software Requirement Specification](./Software%20Requirement%20Specification.pdf)
- [High Level Design](./High%20Level%20Design.pdf)

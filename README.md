# Apex UPI: A High-Concurrency PSP Switch & Ledger Engine for HPE Nonstop Platform

Apex UPI is a full-stack, microservices-based UPI (Unified Payments Interface) platform built to simulate and demonstrate the complete payment lifecycle across four core subsystems: PSP Switch, NPCI Network, Banking Switch, and Core Banking System (CBS).

The platform is designed for educational and demonstration purposes and reflects the real-world architecture of a UPI-compliant payment system as per NPCI specifications.

---

## Technology Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Messaging | Apache Kafka |
| Database | PostgreSQL 14+ |
| Cache | Redis 7 |
| Build | Apache Maven 3.8+ |
| Dashboard | HTML / JavaScript (operations dashboard) |

---

## Repository Structure

```text
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
| --- | --- |
| [PSP Switch Docs](Documentation/PSP%20Switch%20Docs/README.md) | Architecture overview, all 9 service specs, Kafka event contracts, data models, configuration |
| [Banking Switch Docs](Documentation/Banking%20Switch%20Docs/README.md) | Architecture overview, all 5 service specs, saga flow, Kafka event contracts, data models, configuration |
| [Core Banking Services Docs](Documentation/Core%20Banking%20Services%20Docs/README.md) | CBS service overview, Account/Debit/Credit/Ledger service specs, REST API reference, data models |
| [Deployment Docs](Documentation/Deployment%20Docs/README.md) | VM topology for VirtualBox and Azure, database setup, network and port reference |

---

## Deployment

The platform supports two deployment environments. In both cases all services run as standalone Java processes on virtual machines.

### Local -- VirtualBox (4 VMs)

| VM | Subsystem | Key Services |
| --- | --- | --- |
| VM 1 | PSP Switch | 9 microservices, Kafka, Redis, PostgreSQL (`apexupi`) |
| VM 2 | Banking Switch | 5 microservices, PostgreSQL (`bankswitch_db`) |
| VM 3 | Core Banking System | CBS service, PostgreSQL (`cbs_db`) |
| VM 4 | NPCI Network | NPCI service, PostgreSQL (`npci_db`) |

### Cloud -- Azure (3 VMs)

| VM | Subsystem | Key Services |
| --- | --- | --- |
| VM 1 | PSP Switch | 9 microservices, Kafka, Redis, PostgreSQL (`apexupi`) |
| VM 2 | NPCI Network | NPCI service, PostgreSQL (`npci_db`) |
| VM 3 | Banking Services | Banking Switch + CBS, PostgreSQL (`bankswitch_db`, `cbs_db`) |

For full setup instructions, database scripts, and port reference, see [Deployment Docs](Documentation/Deployment%20Docs/README.md).

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

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming conventions, commit message format, and pull request checklist.

---

## Project Documents

- [Business Requirement Document](Business%20Requirement%20Document.pdf)
- [Software Requirement Specification](Software%20Requirement%20Specification.pdf)
- [High Level Design](High%20Level%20Design.pdf)

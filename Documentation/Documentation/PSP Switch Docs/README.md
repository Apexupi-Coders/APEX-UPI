# APEX-UPI PSP Switch — Documentation Index

## About This Document Set

This directory contains the complete technical documentation for the APEX-UPI Payment Service Provider (PSP) Switch. The documentation covers architecture, all microservices, data models, Kafka event contracts, API specifications, configuration, deployment, and design decisions.

This documentation is intended for technical reviewers, engineers, and architects evaluating the APEX-UPI reference implementation of a UPI PSP Switch.

---

## Document Structure

| File | Description |
|---|---|
| [01_PSP_Switch_Overview.md](./01_PSP_Switch_Overview.md) | System overview, purpose, and architecture |
| [02_Service_Catalog.md](./02_Service_Catalog.md) | Catalog of all PSP Switch microservices with responsibilities |
| [03_TPAP_Ingress_Service.md](./03_TPAP_Ingress_Service.md) | Authenticated HTTP entry point for Third-Party Application Providers |
| [04_Transaction_Orchestrator.md](./04_Transaction_Orchestrator.md) | 10-step saga engine — central coordinator of all payment flows |
| [05_NPCI_Adapter.md](./05_NPCI_Adapter.md) | PSP-to-NPCI network bridge via Kafka and REST/XML |
| [06_NPCI_Response_Consumer.md](./06_NPCI_Response_Consumer.md) | NPCI XML callback receiver and Kafka relay |
| [07_Rules_Validation_Service.md](./07_Rules_Validation_Service.md) | Pre-authorization rules engine for transaction validation |
| [08_Ledger_Service.md](./08_Ledger_Service.md) | Immutable financial ledger recording all transaction outcomes |
| [09_PSP_Ledger_Service.md](./09_PSP_Ledger_Service.md) | PSP-level ledger for internal fund movement tracking |
| [10_Audit_Service.md](./10_Audit_Service.md) | Compliance audit trail consuming Kafka events |
| [11_TPAP_Egress_Service.md](./11_TPAP_Egress_Service.md) | Outbound webhook dispatcher delivering results to registered TPAPs |
| [12_Kafka_Event_Contracts.md](./12_Kafka_Event_Contracts.md) | All Kafka topics, schemas, and producer/consumer matrix |
| [13_Data_Models.md](./13_Data_Models.md) | Database schemas, JPA entities, and data flow |
| [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md) | Infrastructure setup and deployment on VirtualBox and Azure |
| [15_Design_Decisions.md](./15_Design_Decisions.md) | Architectural Decision Records (ADRs) and engineering rationale |

---

## Scope

This documentation covers the PSP Switch domain only. The Banking Switch (`banking-switch`), NPCI-CBS bridge (`npci-cbs`, `npci-ledger-service`), and Core Banking System (`cbs`) are separate bounded contexts and are not covered here.

---

## Technology Stack Summary

| Category | Technology | Version |
|---|---|---|
| Language | Java | 17 / 21 |
| Framework | Spring Boot | 3.1.x / 3.2.x / 3.3.x |
| Messaging | Apache Kafka | 3.4+ |
| Database | PostgreSQL | 15 / 16 |
| Cache | Redis | 7 |
| Build | Apache Maven | 3.8+ |
| Local Deployment | Oracle VirtualBox | 7.x |
| Cloud Deployment | Microsoft Azure | Virtual Machines, Azure DB for PostgreSQL, Azure Cache for Redis |

---

*Repository: https://github.com/Apexupi-Coders/APEX-UPI*

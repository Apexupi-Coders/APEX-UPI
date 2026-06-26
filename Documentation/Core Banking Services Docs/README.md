# Core Banking Services Documentation

## Contents

| Document | Description |
|---|---|
| [01_CBS_Overview.md](./01_CBS_Overview.md) | Service purpose, architecture position, technology stack, and deployment topology |
| [02_Account_Service.md](./02_Account_Service.md) | Balance enquiry logic, transaction isolation, and REST endpoint |
| [03_Debit_Service.md](./03_Debit_Service.md) | Debit operation, balance validation, pessimistic locking, and concurrency safety |
| [04_Credit_Service.md](./04_Credit_Service.md) | Credit operation, atomicity, pessimistic locking, and ledger integration |
| [05_Ledger_Service.md](./05_Ledger_Service.md) | Internal bookkeeping, MANDATORY propagation, and immutable ledger design |
| [06_REST_API_Reference.md](./06_REST_API_Reference.md) | Full API reference: endpoints, request/response schemas, HTTP status codes, and examples |
| [07_Data_Models.md](./07_Data_Models.md) | JPA entities, database DDL, DTOs, and repository details |
| [08_Configuration_and_Deployment.md](./08_Configuration_and_Deployment.md) | VM topology, database setup, build, configuration properties, startup, and seed data |

## System Context

The Core Banking System is the account authority in the APEX-UPI platform. It processes balance enquiry, debit, and credit operations against a PostgreSQL database with full transactional integrity. It is consumed exclusively by the CBS Adapter of the Banking Switch via a REST HTTP API and has no dependency on Kafka or any other messaging infrastructure.

## Deployment

- **Local (VirtualBox):** CBS runs on its own dedicated VM (VM 3 of 4).
- **Cloud (Azure):** CBS is co-hosted with the Banking Switch on the Banking Services VM (VM 3 of 3).

Refer to [08_Configuration_and_Deployment.md](./08_Configuration_and_Deployment.md) for full details.

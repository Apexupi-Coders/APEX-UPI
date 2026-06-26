# Banking Switch Documentation

## Contents

| Document | Description |
|---|---|
| [01_Banking_Switch_Overview.md](./01_Banking_Switch_Overview.md) | System architecture, component inventory, transaction types, Kafka topic summary, and deployment topology |
| [02_NPCI_Request_Listener.md](./02_NPCI_Request_Listener.md) | HTTP endpoints, validation logic, XML parsing, and Kafka event publication |
| [03_Transaction_Orchestrator.md](./03_Transaction_Orchestrator.md) | Saga pattern, state machine, CBS routing logic, and PostgreSQL schema |
| [04_CBS_Adapter.md](./04_CBS_Adapter.md) | CBS HTTP client, operation dispatch, error handling, and Kafka event schemas |
| [05_NPCI_Response_Adapter.md](./05_NPCI_Response_Adapter.md) | XML response construction and NPCI HTTP callback dispatch |
| [06_Bank_Ledger_Service.md](./06_Bank_Ledger_Service.md) | Bank-side ledger, Kafka confirmation consumers, and data model |
| [07_Kafka_Event_Contracts.md](./07_Kafka_Event_Contracts.md) | All topic names, producers, consumers, field schemas, and examples |
| [08_Data_Models.md](./08_Data_Models.md) | All JPA entities, DDL, and Kafka event model classes |
| [09_Configuration_and_Deployment.md](./09_Configuration_and_Deployment.md) | VM topology, database setup, build instructions, startup order, and configuration properties |

## System Context

The Banking Switch processes inbound UPI requests routed by NPCI on behalf of a member bank. It handles three transaction types: balance enquiry (ReqBalEnq), payment debit (ReqPay), and payee credit (ReqCredit). Internal coordination between its five services is entirely event-driven via Apache Kafka.

## Deployment

- **Local (VirtualBox):** Banking Switch runs on its own dedicated VM (VM 2 of 4).
- **Cloud (Azure):** Banking Switch services are co-hosted with CBS on a shared Banking Services VM (VM 3 of 3).

Refer to [09_Configuration_and_Deployment.md](./09_Configuration_and_Deployment.md) for full details.

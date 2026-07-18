# Banking Switch Orchestrator

## Overview
The Banking Switch Orchestrator is the central event-driven microservice responsible for coordinating transaction state transitions across the PSP network and external banking integrations.

## Responsibilities
- Receives payment initiation events from the TPAP Ingress via Kafka
- Orchestrates the full transaction lifecycle: `SUBMITTED → SUCCESS | FAILED | COMPENSATED`
- Coordinates with NPCI Adapter for payment routing and response handling
- Triggers CBS debit/credit operations via the Bank Ledger Service
- Publishes callback events for downstream PSP reconciliation

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event-driven messaging)
- PostgreSQL (transaction state persistence)

## Running Locally
```bash
mvn spring-boot:run
```

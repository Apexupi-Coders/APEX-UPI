# Transaction Orchestrator

## Overview
The Transaction Orchestrator is the core state machine of the APEX-UPI PSP Switch. It manages the complete lifecycle of every UPI transaction from initiation through to final settlement.

## Responsibilities
- Drives transaction state transitions: `SUBMITTED → SUCCESS | FAILED | COMPENSATED`
- Coordinates between the NPCI Adapter, CBS, and Ledger services via Kafka
- Enforces idempotency using Redis distributed locks
- Initiates automated compensation flows for failed transactions

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event-driven orchestration)
- PostgreSQL (transaction state persistence)
- Redis (distributed locking)

## Running Locally
```bash
mvn spring-boot:run
```

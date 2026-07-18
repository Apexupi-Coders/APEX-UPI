# PSP Ledger Service

## Overview
The PSP Ledger Service maintains an immutable, double-entry accounting ledger for all transactions processed through the APEX-UPI PSP Switch.

## Responsibilities
- Records every debit and credit entry for each UPI transaction
- Guarantees idempotency using Redis-backed distributed locks
- Provides reconciliation data for auditing and dispute resolution
- Exposes ledger query APIs for the operations dashboard

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event consumption)
- PostgreSQL (ledger persistence)
- Redis (idempotency locking)

## Running Locally
```bash
mvn spring-boot:run
```

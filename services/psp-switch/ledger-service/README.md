# Ledger Service

## Overview
The Ledger Service maintains the canonical transaction ledger for the APEX-UPI PSP Switch, recording all financial movements with double-entry accounting precision.

## Responsibilities
- Processes debit and credit entries for every transaction state change
- Enforces strict balance consistency across all UPI accounts
- Provides ledger snapshots for reconciliation and end-of-day settlement
- Exposes query APIs consumed by the operations and monitoring dashboard

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event consumption)
- PostgreSQL (ledger persistence)

## Running Locally
```bash
mvn spring-boot:run
```

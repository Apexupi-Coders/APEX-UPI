# Rules Validation Service

## Overview
The Rules Validation Service enforces real-time business rules and fraud detection logic on every UPI transaction before it is accepted for processing by the PSP Switch.

## Responsibilities
- Validates transaction amounts against configurable per-day and per-transaction limits
- Enforces VPA (Virtual Payment Address) validity and registration checks
- Performs device fingerprint and risk scoring checks
- Rejects non-compliant transactions before they enter the Kafka pipeline

## Tech Stack
- Java 21 / Spring Boot 3
- PostgreSQL (rules configuration store)

## Running Locally
```bash
mvn spring-boot:run
```

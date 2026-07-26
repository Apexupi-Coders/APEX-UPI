# Audit Service

## Overview
The Audit Service is a lightweight microservice that consumes all transaction lifecycle events from Apache Kafka and persists a tamper-proof audit trail for every UPI transaction processed by the APEX-UPI PSP Switch.

## Responsibilities
- Subscribes to all Kafka topics across the PSP Switch network
- Records immutable audit log entries for compliance and regulatory requirements
- Supports forensic investigation and dispute resolution workflows

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event consumption)
- PostgreSQL (audit log persistence)

## Running Locally
```bash
mvn spring-boot:run
```

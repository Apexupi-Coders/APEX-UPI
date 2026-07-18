# TPAP Egress Service

## Overview
The TPAP Egress Service handles all outbound webhook callbacks from the APEX-UPI PSP Switch back to the originating Third-Party Application Provider (TPAP) upon transaction completion.

## Responsibilities
- Consumes final transaction state events from Kafka
- Delivers HTTP webhook callbacks to registered TPAP endpoints
- Implements retry logic with exponential backoff for failed deliveries
- Logs all callback attempts for audit and debugging

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event consumption)
- PostgreSQL (callback state tracking)

## Running Locally
```bash
mvn spring-boot:run
```

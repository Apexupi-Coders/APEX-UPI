# NPCI Service

## Overview
The NPCI Service provides the core simulation layer for the National Payments Corporation of India (NPCI) within the APEX-UPI platform. It replicates NPCI behavior for end-to-end testing and executive demonstrations.

## Responsibilities
- Simulates NPCI payment authorization for PAY and COLLECT flows
- Supports configurable failure and compensation modes for QA testing
- Provides a REST control API for toggling NPCI failure scenarios in real time
- Maintains transaction response logs for traceability

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event-driven messaging)

## Running Locally
```bash
mvn spring-boot:run
```

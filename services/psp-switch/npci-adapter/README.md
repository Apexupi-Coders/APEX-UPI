# NPCI Adapter

## Overview
The NPCI Adapter is the bridge between the APEX-UPI PSP Switch and the National Payments Corporation of India (NPCI) network. It translates internal PSP Switch events into NPCI-compliant protocol messages.

## Responsibilities
- Translates internal payment events into NPCI UPI Common Library specification-compliant requests
- Handles PAY and COLLECT flow routing to NPCI
- Processes NPCI responses and publishes results back to the orchestration layer
- Simulates NPCI network behavior in demo and test environments

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event-driven messaging)

## Running Locally
```bash
mvn spring-boot:run
```

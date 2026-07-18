# NPCI Response Consumer

## Overview
The NPCI Response Consumer listens for inbound transaction resolution messages from the NPCI network and processes them to finalize transaction states within the PSP Switch.

## Responsibilities
- Consumes NPCI response events (SUCCESS, FAILURE, TIMEOUT) from Kafka
- Translates NPCI protocol responses into internal PSP Switch transaction state updates
- Triggers compensation flows for timed-out or failed NPCI responses
- Publishes final state events to downstream ledger and egress services

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event consumption and publishing)

## Running Locally
```bash
mvn spring-boot:run
```

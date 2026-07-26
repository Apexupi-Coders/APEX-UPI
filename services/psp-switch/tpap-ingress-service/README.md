# TPAP Ingress Service

## Overview
The TPAP Ingress Service is the secure edge gateway of the APEX-UPI PSP Switch. It is the single public-facing entry point for all Third-Party Application Providers (TPAPs) such as PhonePe and Google Pay.

## Responsibilities
- Authenticates all incoming TPAP requests using `X-TPAP-ID` and `X-TPAP-API-Key` headers
- Validates payment request payloads for schema and business rule compliance
- Publishes accepted transactions to the Kafka `payment.initiation` topic
- Routes webhook callbacks back to the originating TPAP upon transaction completion

## Security
All requests must include:
- `X-TPAP-ID` — registered TPAP identifier
- `X-TPAP-API-Key` — HMAC-validated API key

Unauthorized requests are rejected immediately with `HTTP 401`.

## Tech Stack
- Java 21 / Spring Boot 3
- Apache Kafka (event publishing)
- PostgreSQL (idempotency store)

## Running Locally
```bash
mvn spring-boot:run
```

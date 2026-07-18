# CBS (Core Banking System)

## Overview
The CBS service is the simulated Core Banking System for the APEX-UPI platform. It manages customer accounts, validates UPI PINs, and processes the actual debit and credit operations for UPI transactions.

## Responsibilities
- Maintains virtual bank accounts with real-time balance tracking
- Validates UPI PINs during payment authorization
- Processes debit operations for payer accounts and credit operations for payee accounts
- Supports the cbs_debit and cbs_credit database schemas for double-entry settlement

## Tech Stack
- Java 21 / Spring Boot 3
- PostgreSQL (account and balance management)
- Apache Kafka (event-driven debit/credit processing)

## Running Locally
```bash
mvn spring-boot:run
```

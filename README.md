<div align="center">
  <h1>Apex UPI Platform</h1>
  <p><b>A High-Concurrency, Resilient PSP Switch & Ledger Engine</b></p>

  [![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://java.com/)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
  [![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-Event--Driven-black.svg)](https://kafka.apache.org/)
  [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14%2B-blue.svg)](https://www.postgresql.org/)
  [![Redis](https://img.shields.io/badge/Redis-Idempotency-red.svg)](https://redis.io/)
  [![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF.svg)](https://github.com/features/actions)
</div>

---

## Executive Summary
Apex UPI is a full-stack, distributed microservices platform built to simulate and orchestrate the complete lifecycle of a Unified Payments Interface (UPI) transaction. Designed to handle high concurrency while guaranteeing data consistency, it perfectly mirrors the real-world architecture of a UPI-compliant payment system interfacing with the NPCI Network and Core Banking Systems (CBS).

## Core Subsystems
The platform is divided into four strictly isolated network zones:
1. **PSP Switch:** Ingress gateway, idempotency validation, distributed transaction orchestration (Saga), and audit logging.
2. **Banking Switch:** Intermediary routing and banking logic.
3. **NPCI Network Simulator:** Simulates the national payment gateway, including unpredictable latency and network timeouts.
4. **Core Banking System (CBS):** Double-entry ledger system handling physical account debits and credits.

---

## Resilience & Reliability Engineering
A primary focus of this architecture is **fault tolerance** in distributed systems.
* **Dual-Layer Idempotency:** Implemented Redis (fast-path) backed by PostgreSQL (durable-path) utilizing SHA-256 request signatures to prevent duplicate transaction execution during network retries.
* **Distributed Sagas with Compensations:** Used an event-driven choreography saga pattern via Kafka. If CBS debits fail or NPCI rejects a transaction, compensating events automatically trigger financial reversals to guarantee eventual consistency.
* **Automated Reconciliation Sweep:** A resilient `@Scheduled` daemon process running under `REQUIRES_NEW` transaction isolation that sweeps the database for stuck (`UNKNOWN`) transactions and re-queries upstream APIs to resolve indeterminate states.
* **Data Security at Rest:** Sensitive PII data is AES-encrypted at the database layer via a centralized `DataCryptoService` prior to persistence.
* **Resilient Messaging:** Guaranteed message delivery utilizing Kafka `whenComplete` asynchronous callbacks and Dead Letter Queue (DLQ) patterns.

---

## Testing & Performance Engineering
To validate the resilience and scalability of the platform under high concurrency, we developed specialized testing artifacts:
* **JMeter Load Profiles (`performance-tests/`):** Contains `executive-demo.jmx` for high-throughput stress testing and `demo-failures.jmx` to simulate upstream NPCI timeouts and CBS insufficiency errors under load.
* **Postman Collections:** Comprehensive API test suites encompassing idempotency replay validation and full transaction lifecycle testing.

---

## Repository Structure

```text
APEX-UPI/
├── services/
│   ├── psp-switch/               # 9 Core PSP Microservices (Ingress, Ledger, Audit, Orchestrator)
│   ├── banking-switch/           # 5 Banking Switch Microservices
│   ├── cbs/                      # Core Banking Ledger System
│   └── npci/                     # NPCI Network Simulator
├── Documentation/
│   ├── PSP Switch Docs/          # Kafka event contracts, Saga patterns, API specs
│   ├── Banking Switch Docs/      # Core banking saga flows
│   └── Deployment Docs/          # VM topologies, networking configurations
├── performance-tests/            # JMeter scripts (Executive Demo, Failure Testing)
├── integration/                  # Docker/Integration configurations
├── dashboard-ui/                 # Real-time HTML/JS Operations Dashboard
└── Live Monitoring Links/        # Active Ngrok tunnel endpoints for demo control
```

---

## Technology Stack
* **Language:** Java 17
* **Framework:** Spring Boot 3.3.0, Spring Data JPA, Spring Kafka
* **Messaging:** Apache Kafka (Event-Driven Architecture)
* **Databases:** PostgreSQL 14+ (Relational), Redis 7 (In-Memory Cache)
* **Build & CI/CD:** Apache Maven, GitHub Actions
* **Testing:** JMeter (Load & Stress), Postman (API functional)
* **Deployment:** Distributed VirtualBox Topologies & Microsoft Azure VMs

---

## CI/CD & Code Quality
The repository enforces a strict continuous integration pipeline utilizing **GitHub Actions**:
* Automated Maven builds and dependency caching.
* Static code analysis utilizing `Checkstyle`, `PMD`, and `SpotBugs`.
* Zero-downtime deployment pipelines targeting Azure Virtual Machines (`ci-tpap-ingress`, `ci-transaction-orchestrator`).

---

## Getting Started

### Local Build Requirements
* Java 17, PostgreSQL 14, Apache Kafka 3.x, Redis 7+

### Build & Run
```bash
# Build the service
cd services/psp-switch/tpap-ingress-service
mvn clean package -DskipTests

# Run locally
java -jar target/tpap-ingress-service-*.jar --spring.config.location=application.yml
```
*For detailed Azure and VirtualBox topology startup sequences, please consult the [Deployment Documentation](Documentation/Deployment%20Docs/README.md).*

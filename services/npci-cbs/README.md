# UPI NPCI + CBS — Complete Demo (v3)
## My Part: NPCI Router + CBS Service

---

## Project Scope

This project implements the NPCI Router and CBS Service layers of UPI.
PSP Switch belongs to another team. This project simulates their input
via a test endpoint that publishes directly to Kafka.

```
[PSP Switch]           NOT my part (another team)
      | Kafka
[NPCI Router]          MY PART
  |-- VpaRegistry
  |-- NpciAdapterImpl
  |-- NpciRoutingService
      | Kafka
[CBS Service]          MY PART
  |-- CbsGateway
  |-- CbsDebitService
  |-- CbsCreditService
      |
[Debit DB + Credit DB] MY PART
      | Kafka
[Dashboard + Webhook]  Supporting services
```

---

## Real UPI vs This Project

### 1. Protocol

Real UPI uses ISO 8583 binary protocol and NPCI proprietary XML messages.
This project uses custom JSON over Kafka with simulated NpciRequest POJOs.

In real UPI every message between PSP, NPCI, and banks uses ISO 8583 — a
binary financial protocol where every field has a fixed bit position. Your
NpciRequest POJO simulates this boundary. The NpciProtocolAdapter interface
is designed so a real ISO 8583 implementation can be swapped in without
changing any routing logic.

### 2. Transport

Real UPI uses dedicated NPCI leased lines, secure VPN tunnels, HSM hardware
encryption, and mutual TLS certificates. This project uses Kafka topics over
a local Docker network with no encryption.

In real UPI banks connect to NPCI over dedicated leased lines — not the
internet. Each message is encrypted by a Hardware Security Module. Kafka
simulates this async messaging pattern architecturally. The publish/consume
model is identical — only the transport and encryption differ.

### 3. Bank Model

In real UPI the payer bank and payee bank are completely separate organizations
each with their own CBS and their own NPCI connection. In this project one
CBS Service handles both sides using two separate PostgreSQL databases.

cbs_debit (port 5433) = payer's bank CBS
cbs_credit (port 5434) = payee's bank CBS

---

## Services

| Service | Port | Owner |
|---|---|---|
| NPCI Router | 8082 | Mine |
| CBS Service | 8083 | Mine |
| Dashboard | 8084 | Supporting |
| Webhook | 8085 | Supporting |
| CBS Debit DB | 5433 | Mine |
| CBS Credit DB | 5434 | Mine |

PSP Switch is not included. Use /api/npci/test/pay instead.

---

## Pre-seeded Accounts

| VPA | Name | Balance | Status |
|---|---|---|---|
| alice@sbi | Alice Sharma | Rs.10000 | ACTIVE |
| bob@hdfc | Bob Kumar | Rs.5000 | ACTIVE |
| charlie@icici | Charlie Mehta | Rs.2000 | ACTIVE |
| diana@axis | Diana Patel | Rs.8000 | ACTIVE |
| eve@kotak | Eve Nair | Rs.15000 | ACTIVE |
| merchant@ybl | Test Merchant | Rs.50000 | ACTIVE |
| frozen@hdfc | Frozen Account | Rs.1000 | FROZEN |
| dormant@sbi | Dormant Account | Rs.500 | DORMANT |

---

## Run

```
docker-compose up --build
```

---

## Test Commands

Normal payment:
curl -X POST http://localhost:8082/api/npci/test/pay -H 'Content-Type: application/json' -d '{"payerVpa":"alice@sbi","payeeVpa":"bob@hdfc","amount":"500"}'

Auto-reversal:
curl -X POST http://localhost:8082/api/npci/test/pay -H 'Content-Type: application/json' -d '{"payerVpa":"alice@sbi","payeeVpa":"bob@hdfc","amount":"1000","simulateFailure":"true"}'

Insufficient balance:
curl -X POST http://localhost:8082/api/npci/test/pay -H 'Content-Type: application/json' -d '{"payerVpa":"charlie@icici","payeeVpa":"bob@hdfc","amount":"5000"}'

VPA not found:
curl -X POST http://localhost:8082/api/npci/test/pay -H 'Content-Type: application/json' -d '{"payerVpa":"unknown@xyz","payeeVpa":"bob@hdfc","amount":"500"}'

Frozen account:
curl -X POST http://localhost:8082/api/npci/test/pay -H 'Content-Type: application/json' -d '{"payerVpa":"alice@sbi","payeeVpa":"frozen@hdfc","amount":"500"}'

Self transfer:
curl -X POST http://localhost:8082/api/npci/test/pay -H 'Content-Type: application/json' -d '{"payerVpa":"alice@sbi","payeeVpa":"alice@sbi","amount":"500"}'

Exceeds limit:
curl -X POST http://localhost:8082/api/npci/test/pay -H 'Content-Type: application/json' -d '{"payerVpa":"alice@sbi","payeeVpa":"bob@hdfc","amount":"200000"}'

Balance check:
curl http://localhost:8083/api/cbs/account/alice@sbi/balance

All accounts:
curl http://localhost:8083/api/cbs/accounts

Transaction history:
curl http://localhost:8083/api/cbs/account/alice@sbi/transactions

Reconciliation:
curl http://localhost:8083/api/cbs/reconcile/today

VPA registry:
curl http://localhost:8082/api/npci/vpa/registry

Error codes:
curl http://localhost:8082/api/npci/error-codes

Dashboard:
http://localhost:8084

---

## NPCI Error Codes

U001 - VPA not registered
U002 - Insufficient funds
U003 - Transaction limit exceeded
U004 - Account frozen
U005 - Bank unavailable
U006 - Duplicate transaction
U007 - Self transfer not allowed
U008 - Account dormant
U009 - Invalid VPA format

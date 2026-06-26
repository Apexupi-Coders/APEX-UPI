# Core Banking System — Service Overview

## 1. Purpose

The Core Banking System (CBS) is the authoritative account management service in the APEX-UPI platform. It holds the account balances, processes debit and credit operations with full transactional integrity, and maintains an internal transaction ledger. It is the only service in the system that has write access to account balance data.

The CBS exposes a REST API consumed exclusively by the CBS Adapter of the Banking Switch. It has no awareness of UPI protocols, Kafka, or the PSP Switch. Its interface is intentionally simple: three operations — balance enquiry, debit, and credit — each invoked with a VPA and an amount.

---

## 2. Position in the APEX-UPI Architecture

```
Banking Switch
    |
    | HTTP REST
    |   GET  /cbs/balance/{vpa}
    |   POST /cbs/debit
    |   POST /cbs/credit
    v
Core Banking System (CBS)
    |
    v
PostgreSQL (cbs_db)
    Tables: accounts, transaction_ledger, user_credentials
```

The CBS does not initiate any outbound calls. All interaction is inbound from the Banking Switch's CBS Adapter.

---

## 3. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `cbs-service` |
| Group ID | `com.bankingswitch` |
| Version | `0.0.1-SNAPSHOT` |
| Base Package | `com.bankingswitch.cbs` |
| Java Version | 17 |
| Spring Boot Version | 3.2.5 |
| Default Port | 9090 (CBS VM) |

---

## 4. Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Persistence | Spring Data JPA with Hibernate |
| Database | PostgreSQL |
| Build Tool | Apache Maven |
| Boilerplate Reduction | Project Lombok |

---

## 5. Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API exposure |
| `spring-boot-starter-data-jpa` | JPA/Hibernate for PostgreSQL access |
| `postgresql` | PostgreSQL JDBC driver |
| `lombok` | Boilerplate reduction |

---

## 6. Source Structure

```
com.bankingswitch.cbs
├── CbsServiceApplication.java
├── config/
│   ├── DataSourceConfig.java                 Placeholder; relies on Spring Boot autoconfiguration
│   └── TransactionIsolationConfig.java       Documents isolation strategy (applied per-method)
├── controller/
│   └── CbsOperationsController.java          REST endpoints: /cbs/balance, /cbs/debit, /cbs/credit
├── model/
│   ├── dto/
│   │   ├── BalanceResponse.java              Response DTO for balance enquiry
│   │   ├── DebitRequest.java                 Request DTO for debit operation
│   │   ├── CreditRequest.java                Request DTO for credit operation
│   │   └── OperationResponse.java            Response DTO for debit and credit operations
│   └── entity/
│       ├── Account.java                      JPA entity mapped to table: accounts
│       ├── TransactionLedger.java            JPA entity mapped to table: transaction_ledger
│       └── UserCredential.java               JPA entity mapped to table: user_credentials
├── repository/
│   ├── AccountRepository.java                JPA repository with pessimistic locking query
│   ├── TransactionLedgerRepository.java      JPA repository for ledger entries
│   └── UserCredentialRepository.java         JPA repository for credential lookup
└── service/
    ├── AccountService.java                   Balance enquiry with READ_COMMITTED isolation
    ├── DebitService.java                     Debit with SERIALIZABLE isolation + pessimistic lock
    ├── CreditService.java                    Credit with SERIALIZABLE isolation + pessimistic lock
    └── LedgerService.java                    Appends ledger entries within caller's transaction
```

---

## 7. REST API Summary

| Method | Path | Operation | Description |
|---|---|---|---|
| GET | `/cbs/balance/{vpa}` | Balance Enquiry | Returns the current balance of the account identified by VPA |
| POST | `/cbs/debit` | Debit | Debits the specified amount from the account. Requires sufficient balance. |
| POST | `/cbs/credit` | Credit | Credits the specified amount to the account. Always succeeds if the account exists. |

---

## 8. Transaction Isolation Strategy

The CBS applies different transaction isolation levels per operation, reflecting the risk profile of each:

| Operation | Isolation Level | Locking | Rationale |
|---|---|---|---|
| Balance Enquiry | `READ_COMMITTED` | None | Read-only; dirty reads are prevented; no lock contention needed |
| Debit | `SERIALIZABLE` | Pessimistic write lock | Prevents race conditions and double-spend between concurrent debit requests on the same account |
| Credit | `SERIALIZABLE` | Pessimistic write lock | Prevents race conditions on concurrent credits; ensures ledger consistency |

Isolation levels are declared using `@Transactional(isolation = Isolation.SERIALIZABLE)` and `@Transactional(isolation = Isolation.READ_COMMITTED)` directly on the service methods.

---

## 9. Accounts and VPA

The `accounts` table uses the VPA (Virtual Payment Address) as the primary key. Each VPA uniquely identifies one account. The account also stores the linked bank account number and a hashed PIN for authentication purposes.

---

## 10. Database

The CBS owns the `cbs_db` PostgreSQL database on the CBS VM.

| Table | Purpose |
|---|---|
| `accounts` | Account balances, VPA-to-account mapping, status, PIN hash |
| `transaction_ledger` | Immutable financial movement record for every debit and credit |
| `user_credentials` | VPA-to-MPIN hash and device binding |

Database creation script: `services/cbs/setup/create-db.sql`

---

## 11. Deployment

### Local Deployment (VirtualBox)

The CBS runs on its own dedicated virtual machine:

| VM | Services Hosted |
|---|---|
| VM 3 — Core Banking System | CBS service, PostgreSQL for `cbs_db` |

### Cloud Deployment (Azure)

In the Azure deployment, the CBS is co-located with the Banking Switch on a shared VM:

| VM | Services Hosted |
|---|---|
| VM 3 — Banking Services | Banking Switch microservices + CBS service, shared PostgreSQL instance |

In this configuration the CBS Adapter communicates with the CBS service over the VM's internal network interface.

Refer to [08_Configuration_and_Deployment.md](./08_Configuration_and_Deployment.md) for full setup instructions.

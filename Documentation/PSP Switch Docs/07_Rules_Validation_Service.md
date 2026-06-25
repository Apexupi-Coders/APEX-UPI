# Rules Validation Service

## 1. Purpose

The Rules Validation Service provides a synchronous REST API for pre-authorization transaction validation within the PSP Switch. It executes a configurable chain of business and regulatory rules against incoming payment requests before they are forwarded to NPCI. Any rule failure short-circuits the chain and returns a structured rejection response with the specific rule that failed.

---

## 2. Identification

| Attribute | Value |
|---|---|
| Artifact ID | `rules-validation-service` |
| Group ID | `com.pspswitch` |
| Version | `0.0.1-SNAPSHOT` |
| Java Version | 17 |
| Spring Boot Version | 3.1.5 |
| Default Port | 8085 |
| Base Package | `com.pspswitch.rules` |

---

## 3. Dependencies

| Dependency | Purpose |
|---|---|
| spring-boot-starter-web | REST endpoint serving |
| spring-boot-starter-data-jpa | PostgreSQL persistence for blacklists and summaries |
| spring-boot-starter-data-redis | Redis for velocity/rate-limit counters |
| spring-cloud-starter-openfeign | Feign HTTP client for potential upstream service calls |
| postgresql | JDBC driver |
| lombok | Boilerplate reduction |
| spring-boot-starter-test | Testing |
| spring-cloud-dependencies 2022.0.4 | Spring Cloud BOM |

---

## 4. Source Structure

```
com.pspswitch.rules
├── RulesValidationServiceApplication.java
├── client/
│   └── RulesValidationFeignClient.java     Feign client for external rule data sources
├── config/
│   └── RulesProperties.java                @ConfigurationProperties for rule thresholds
├── controller/
│   └── ValidationController.java           POST /rules/validate
├── entity/
│   ├── Blacklist.java                       JPA entity -> blacklist table
│   ├── TransactionSummary.java              JPA entity -> transaction_summary table
│   └── ValidationLog.java                   JPA entity -> validation_logs table
├── model/
│   ├── ValidationRequest.java               Input DTO
│   ├── ValidationResponse.java              Output DTO
│   └── ValidationDecision.java              Enum: APPROVED / REJECTED
├── repository/
│   ├── BlacklistRepository.java             findByVpa(String vpa)
│   ├── TransactionSummaryRepository.java    findByVpaAndDate(...)
│   └── ValidationLogRepository.java         save(ValidationLog)
├── rules/
│   ├── Rule.java                            Rule interface: evaluate(ValidationRequest)
│   ├── AmountRule.java                      Checks min/max amount bounds
│   ├── VpaFormatRule.java                   Validates VPA format regex
│   ├── BlacklistRule.java                   Checks payer/payee against blacklist
│   ├── DailyLimitRule.java                  Enforces per-VPA daily transaction limit
│   ├── VelocityRule.java                    Enforces per-minute transaction velocity
│   └── DuplicateDetectionRule.java          Redis-based short-window duplicate detection
└── service/
    └── ValidationService.java               Orchestrates rule chain execution
```

---

## 5. API Endpoint

| Method | Path | Description |
|---|---|---|
| POST | `/rules/validate` | Submit a transaction for rule-chain validation |

### Request Body — ValidationRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `txnId` | String | Yes | PSP transaction identifier |
| `txnRef` | String | Yes | Merchant transaction reference |
| `payerVpa` | String | Yes | Payer UPI VPA |
| `payeeVpa` | String | Yes | Payee UPI VPA |
| `amount` | BigDecimal | Yes | Transaction amount |
| `currency` | String | Yes | Currency code |
| `mode` | String | Yes | UPI mode code |
| `timestamp` | String | Yes | ISO-8601 request timestamp |

### Response Body — ValidationResponse

| Field | Type | Description |
|---|---|---|
| `txnId` | String | Echo of the submitted transaction ID |
| `decision` | String | `APPROVED` or `REJECTED` |
| `rejectionReason` | String | Populated on `REJECTED`; null on `APPROVED` |
| `failedRule` | String | Name of the rule that caused rejection |
| `validatedAt` | String | ISO-8601 timestamp of evaluation |

---

## 6. Validation Rules

The `ValidationService` iterates the rule chain in priority order. The first rule to reject short-circuits execution.

### Rule 1 — AmountRule

Validates that the transaction amount is within configured min/max bounds.

| Check | Condition for rejection |
|---|---|
| Minimum amount | `amount < rulesProperties.minAmount` |
| Maximum amount | `amount > rulesProperties.maxAmount` |

Configured via `RulesProperties` bound to `rules.min-amount` and `rules.max-amount` in `application.properties`.

### Rule 2 — VpaFormatRule

Validates that both `payerVpa` and `payeeVpa` conform to the UPI VPA format.

- **Pattern:** `[a-zA-Z0-9._-]+@[a-zA-Z]+`
- **Rejects if:** Either VPA does not match the pattern

### Rule 3 — BlacklistRule

Checks whether the payer or payee VPA is present in the `blacklist` table.

- **Mechanism:** `BlacklistRepository.existsByVpa(payerVpa)` and `existsByVpa(payeeVpa)`
- **Rejects if:** Either VPA is found in the blacklist

### Rule 4 — DailyLimitRule

Enforces a per-VPA daily transaction count and volume limit.

- **Mechanism:** Queries `transaction_summary` table for the current date and payer VPA
- **Rejects if:** `dailyCount >= rulesProperties.maxDailyCount` or `dailyVolume + amount > rulesProperties.maxDailyVolume`

### Rule 5 — VelocityRule

Enforces a per-VPA per-minute transaction rate limit.

- **Mechanism:** Redis counter incremented on each request; key format `velocity::{payerVpa}::{minuteSlot}` with 60-second TTL
- **Rejects if:** Counter exceeds `rulesProperties.maxPerMinute`

### Rule 6 — DuplicateDetectionRule

Short-window duplicate detection within a 10-minute window.

- **Mechanism:** Redis key `dup::{txnRef}::{payerVpa}::{payeeVpa}::{amount}` with 600-second TTL
- **Rejects if:** Key already exists (transaction submitted twice within 10 minutes with identical parameters)

---

## 7. Rule Chain Configuration — RulesProperties

All thresholds are externalized and configurable without code changes.

| Property | Default | Description |
|---|---|---|
| `rules.min-amount` | `1.00` | Minimum allowed transaction amount (INR) |
| `rules.max-amount` | `100000.00` | Maximum allowed transaction amount (INR) |
| `rules.max-daily-count` | `20` | Maximum daily transaction count per VPA |
| `rules.max-daily-volume` | `200000.00` | Maximum daily volume per VPA (INR) |
| `rules.max-per-minute` | `5` | Maximum transactions per minute per VPA |

---

## 8. Audit Logging

Every validation result (approved or rejected) is persisted as a `ValidationLog` entity.

`ValidationLog` entity fields:

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `txnId` | VARCHAR | Transaction identifier |
| `payerVpa` | VARCHAR | Payer VPA |
| `payeeVpa` | VARCHAR | Payee VPA |
| `amount` | DECIMAL | Amount validated |
| `decision` | VARCHAR | `APPROVED` or `REJECTED` |
| `failedRule` | VARCHAR | Rule name, or null |
| `rejectionReason` | VARCHAR | Reason text, or null |
| `evaluatedAt` | TIMESTAMP | Evaluation timestamp |

---

## 9. Database Schema

### `blacklist` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `vpa` | VARCHAR | Blacklisted UPI VPA |
| `reason` | VARCHAR | Reason for blacklisting |
| `addedAt` | TIMESTAMP | When the entry was added |

### `transaction_summary` Table

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `vpa` | VARCHAR | VPA |
| `summaryDate` | DATE | Calendar date |
| `txnCount` | INTEGER | Cumulative count for the day |
| `txnVolume` | DECIMAL | Cumulative volume for the day |

---

## 10. Building the Service

```bash
cd services/psp-switch/rules-validation-service
mvn clean package -DskipTests
```

The service JAR is output to `target/rules-validation-service-0.0.1-SNAPSHOT.jar`. Deployment instructions for VirtualBox and Azure are covered in [14_Configuration_and_Deployment.md](./14_Configuration_and_Deployment.md).

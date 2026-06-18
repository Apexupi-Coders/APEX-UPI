# APEX-UPI System Analysis Summary

---

## 1. System Architecture

APEX-UPI is a UPI payment processing platform built on Java/Spring Boot microservices with a read-only observability layer.

### Core Infrastructure
| Component | Technology | Port |
|---|---|---|
| Message Broker | Apache Kafka (Confluent 7.4) + Zookeeper | 9092 / 2181 |
| Database | PostgreSQL 16 | 15432 |
| Cache | Redis 7 | 6379 |
| NPCI Mock | WireMock 3.3 | 9090 |

### Microservices
| Service | Role | Port |
|---|---|---|
| `tpap-ingress-service` | Entry point; receives TPAP callbacks, publishes to Kafka | 8080 |
| `transaction-orchestrator` | Core state machine; consumes/produces Kafka, calls NPCI Adapter | 8081 |
| `npci-adapter` | Translates UPI protocol, calls NPCI (WireMock in dev) | 8082 |

### Business Flow (Read-Only, Source of Truth: architecture-diagram.puml)
```
TPAP → Kafka → Transaction Orchestrator → Kafka → NPCI Adapter → NPCI
                                                                       ↓
                                                              Webhook → Ledger
```

### Observability Layer (Separate, Non-Mutating)
- **`operations-dashboard-api`** — Spring Boot, read-only GET endpoints only; connects to PostgreSQL and Kafka via monitoring-only consumer groups (`dashboard-monitor-group`, `dashboard-audit-group`). Must never publish to production topics or mutate transaction state.
- **`dashboard-ui`** — React + TypeScript + Vite + Tailwind + Recharts SPA. Polls the ops API. Hash-based routing, no backend writes.

---

## 2. User Roles

The architecture defines a single explicit role:

| Role | Description | Access |
|---|---|---|
| **Operator** | Operations/monitoring personnel | Read-only view of all dashboard modules via `dashboard-ui` |

No authentication or RBAC system is defined in the current codebase. The observability layer is explicitly designed as a **read-only** interface — no user can mutate state through the dashboard.

---

## 3. Dashboard List

Twelve dashboard modules are defined in the router and side navigation:

| # | Route | Page Name | Data Source |
|---|---|---|---|
| 1 | `#/executive` | Executive Overview | `GET /api/v1/ops/health` (polling 4s) |
| 2 | `#/live` | Live Transaction Monitor | Placeholder — real-time txn feed |
| 3 | `#/events` | Real-Time Event Stream | Kafka monitoring consumer |
| 4 | `#/journey` | Transaction Journey | `GET /api/v1/ops/transactions/{tid}/journey` |
| 5 | `#/kafka` | Kafka Monitoring Center | Kafka consumer group stats |
| 6 | `#/reconciliation` | Reconciliation Dashboard | Read-only PostgreSQL ledger queries |
| 7 | `#/ledger` | Ledger Dashboard | Read-only PostgreSQL |
| 8 | `#/audit` | Audit Dashboard | Read-only PostgreSQL audit tables |
| 9 | `#/errors` | Error Intelligence Center | Error classification by type |
| 10 | `#/health` | Service Health Center | Aggregated service health |
| 11 | `#/architecture` | Architecture Flow Visualization | Static/animated diagram |
| 12 | `#/demo` | Demo Mode | Realistic mock playback (no Kafka needed) |

---

## 4. Navigation Structure

```
SideNav (left sidebar)
├── Overview               → #/executive
├── Live Txns              → #/live
├── Event Stream           → #/events
├── Journey                → #/journey
├── Kafka Center           → #/kafka
├── Reconciliation         → #/reconciliation
├── Ledger                 → #/ledger
├── Audit                  → #/audit
├── Errors                 → #/errors
├── Health                 → #/health
├── Architecture           → #/architecture
└── Demo Mode              → #/demo
```

Layout: `RootLayout` (TopBar + SideNav) wrapping `CommandCenterLayout` wrapping the active page `Outlet`. Hash-based client-side routing via `useRoute` / `navigate` hooks.

---

## 5. API List

### Operations Dashboard API (`operations-dashboard-api` — Spring Boot, read-only)

| Method | Endpoint | Description | Status |
|---|---|---|---|
| GET | `/api/v1/ops/health` | Aggregated service health (TPAP, Orchestrator, NPCI Adapter, Kafka, Redis, PostgreSQL) | Placeholder |
| GET | `/api/v1/ops/transactions/{tid}/journey` | Full state-change timeline for a transaction ID | Placeholder |
| GET | `/api/v1/ops/transactions/search?tr=&pa=` | Search transactions by reference or payee/payer | Placeholder |

> All endpoints are GET-only. No POST, PUT, PATCH, or DELETE endpoints exist or are planned for the dashboard API.

### Planned / Implied (from TODO and page scaffolding)

| Method | Endpoint (proposed) | Description |
|---|---|---|
| GET | `/api/v1/ops/overview` | TPS, success/failure rates, pending count, p95 latency |
| GET | `/api/v1/ops/events/stream` | Real-time business event feed (SSE or polling) |
| GET | `/api/v1/ops/kafka/status` | Kafka topic/consumer group monitoring |
| GET | `/api/v1/ops/reconciliation` | Reconciliation status and mismatches |
| GET | `/api/v1/ops/ledger` | Ledger entries read-only |
| GET | `/api/v1/ops/audit` | Audit log entries |
| GET | `/api/v1/ops/errors` | Classified errors (validation / kafka / callback / db / security) |

### Error Classification Categories (planned)
- Validation errors
- Kafka errors
- Callback errors
- Database errors
- Security errors

---

*Source of truth: APEX-UPI-main.zip — analysed June 2026. All backend endpoints are currently placeholder implementations awaiting real PostgreSQL and Kafka wiring.*

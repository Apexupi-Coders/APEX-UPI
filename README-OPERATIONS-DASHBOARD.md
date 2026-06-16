# APEX-UPI Operations Dashboard (Read-only observability layer)

This repository now includes:

- `dashboard-ui/operations-dashboard/` (React + TypeScript + Vite + Tailwind + Recharts)
- `operations-dashboard-api/` (Spring Boot read-only backend)

## Run (after installing tooling)

### Frontend
```bash
cd dashboard-ui/operations-dashboard
npm install
npm run dev
```

### Backend
```bash
cd operations-dashboard-api
mvn spring-boot:run
```

## Read-only guarantees
- Dashboard UI does not call any mutating endpoints.
- Dashboard API provides only GET endpoints.
- Dashboard API may run monitoring-only Kafka consumers using unique groups.
- Dashboard API must not publish to production topics.
- Dashboard API must not update transaction tables.
- No changes are made to payment processing services.

## What is scaffolded (current state)
- UI app skeleton and styling configuration.
- Backend API endpoints (placeholders) for health and transaction journey.
- Documentation scaffolding.

Next steps:
- Wire backend read-only queries to existing PostgreSQL state/ledger/audit.
- Implement Kafka monitoring consumers for business events mapping.
- Implement full UI modules and real-time event streaming.


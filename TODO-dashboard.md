# Dashboard Implementation TODO (Observability Layer)

## Frontend (dashboard-ui/)
- [ ] Create Vite+React+TypeScript+Tailwind app scaffold under `dashboard-ui/operations-dashboard/`.
- [ ] Add dark/light mode toggle + Tailwind theme configuration.
- [ ] Install and configure Recharts.
- [ ] Implement shared UI primitives: cards, tables, filters, badges, skeleton loaders.
- [ ] Implement global error boundary wrapper for every module.
- [ ] Build modules (read-only):
  - [ ] Executive Overview
  - [ ] Live Transaction Monitor
  - [ ] Real-Time Event Stream
  - [ ] Transaction Journey Timeline
  - [ ] Kafka Monitoring Center
  - [ ] Reconciliation Dashboard
  - [ ] Ledger Dashboard
  - [ ] Audit Dashboard
  - [ ] Error Intelligence Center
  - [ ] Service Health Center
  - [ ] Architecture Flow Visualization (animated path)
  - [ ] Mentor Demo Mode (realistic playback)

## Backend (operations-dashboard-api/)
- [ ] Scaffold Spring Boot service under `operations-dashboard-api/`.
- [ ] Add read-only REST controllers for dashboard data.
- [ ] Implement read-only service health aggregation.
- [ ] Implement PostgreSQL read queries for transaction journey, ledger, audit, reconciliation.
- [ ] Implement Kafka monitoring-only consumers (unique consumer groups):
  - dashboard-monitor-group
  - dashboard-audit-group
- [ ] Convert raw events/log lines into business events for UI.
- [ ] Error intelligence classification (validation/kafka/callback/db/security).
- [ ] Ensure API never mutates transaction state, never publishes to production topics.

## Integration & Validation
- [ ] Wire frontend to backend APIs (read-only endpoints).
- [ ] Run dashboard in demo mode without real Kafka connectivity.
- [ ] Run against local stack (Kafka+Postgres) to validate live mode.
- [ ] Confirm existing transaction flow continues unchanged.


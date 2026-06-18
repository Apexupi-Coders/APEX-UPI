# API Contracts — Operations Dashboard

All endpoints are **GET-only**. The UI expects JSON responses.

## GET /api/v1/ops/overview

Executive KPIs and charts.

```json
{
  "tps": 1420,
  "successRate": 0.978,
  "failureRate": 0.022,
  "pendingCount": 23,
  "p95LatencyMs": 210,
  "timestamp": "2026-06-18T12:00:00Z",
  "revenue": { "total": 3200000, "delta": 4.2, "currency": "INR", "period": "Today" },
  "volumeSeries": [{ "label": "08:00", "value": 980 }],
  "healthStatus": "UP",
  "services": [{ "name": "TPAP Ingress", "status": "UP", "latencyMs": 12 }]
}
```

## GET /api/v1/ops/health

```json
{
  "status": "UP",
  "timestamp": "2026-06-18T12:00:00Z",
  "services": [{ "name": "Kafka", "status": "UP", "latencyMs": 8 }]
}
```

## GET /api/v1/ops/transactions/search

Query params: `transactionId`, `tr`, `pa`, `status`, `page`, `pageSize`

```json
{
  "results": [{ "transactionId": "TXN...", "reference": "REF...", "payer": "user@upi", "payee": "merchant@upi", "amount": 1500, "status": "SUCCESS", "timestamp": "..." }],
  "total": 50,
  "page": 1,
  "pageSize": 10
}
```

## GET /api/v1/ops/transactions/{tid}/journey

```json
{
  "transactionId": "TXN...",
  "status": "SUCCESS",
  "startedAt": "...",
  "completedAt": "...",
  "events": [{ "state": "INITIATED", "service": "TPAP Ingress", "message": "...", "timestamp": "..." }]
}
```

## Other endpoints

- `GET /api/v1/ops/events` → `{ events: [...], timestamp }`
- `GET /api/v1/ops/kafka/status` → `{ brokerStatus, topics: [], consumerGroups: [] }`
- `GET /api/v1/ops/reconciliation` → `{ matched, mismatched, pending, mismatches: [] }`
- `GET /api/v1/ops/ledger` → `{ entries: [] }`
- `GET /api/v1/ops/audit` → `{ entries: [] }`
- `GET /api/v1/ops/errors` → `{ total, byCategory: {}, recent: [] }`

See `assets/js/mock/mockApi.js` for the full mock implementation used in standalone mode.

# operations-dashboard-api

Read-only Spring Boot backend for APEX-UPI operations observability

## API (read-only)
- `GET /api/v1/ops/health`
- `GET /api/v1/ops/transactions/{tid}/journey`
- `GET /api/v1/ops/transactions/search?tr=...&pa=...`

## Run
```bash
mvn spring-boot:run
```

## Notes
- This project is generated as an observability layer.
- It must not modify transaction processing logic, Kafka producers/consumers used by the business flow, DTO contracts, or DB schema.
- Current implementation uses placeholders; next step is wiring real read-only DB/Kafka monitoring.


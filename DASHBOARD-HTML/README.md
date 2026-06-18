# APEX Operations Dashboard (HTML)

Read-only UPI payment observability dashboard for the APEX-UPI platform. Vanilla HTML, CSS, and JavaScript with hash-based routing.

## Quick Start

**Do not open `index.html` directly via `file://`** — use a local HTTP server:

```bash
# Python
python -m http.server 8080

# Node (npx)
npx serve .
```

Open [http://localhost:8080/](http://localhost:8080/) — the app redirects to `#/executive`.

## Standalone Demo Mode

Mock API is **enabled by default** (`USE_MOCK: true`). All 12 dashboard modules work without a Spring Boot backend.

To connect to a real backend, set before scripts load:

```html
<script>
  window.__APEX_CONFIG__ = {
    USE_MOCK: false,
    BASE_URL: "http://localhost:8090"
  };
</script>
```

## Dashboard Routes

| Route | Page |
|---|---|
| `#/executive` | Executive Overview |
| `#/live` | Live Transaction Monitor |
| `#/events` | Real-Time Event Stream |
| `#/journey` | Transaction Journey |
| `#/kafka` | Kafka Monitoring Center |
| `#/reconciliation` | Reconciliation Dashboard |
| `#/ledger` | Ledger Dashboard |
| `#/audit` | Audit Dashboard |
| `#/errors` | Error Intelligence Center |
| `#/health` | Service Health Center |
| `#/architecture` | Architecture Flow |
| `#/demo` | Demo Mode |

## Project Structure

See `PROJECT-STRUCTURE.md` for the full file layout and `APEX-SUMMARY.md` for system architecture context.

## API Endpoints (when mock disabled)

All read-only GET endpoints under `/api/v1/ops/`:

- `/health`, `/overview`
- `/transactions/search`, `/transactions/{tid}/journey`
- `/events`, `/kafka/status`, `/reconciliation`, `/ledger`, `/audit`, `/errors`

 Here's the complete project structure:

```
APEX-DASHBOARD-HTML/
│
├── index.html                              # Shell: TopBar + SideNav + <main> mount point
│
├── assets/
│   ├── css/
│   │   ├── core/
│   │   │   ├── reset.css                   # Normalize / base reset
│   │   │   ├── variables.css               # Theme tokens: navy bg, purple, cyan, glass blur
│   │   │   ├── typography.css              # Font stack, headings, mono for IDs
│   │   │   ├── layout.css                  # Grid, flex helpers, spacing scale
│   │   │   └── utilities.css               # .glass, .badge, .chip, .pill, .scrollbar
│   │   │
│   │   ├── components/
│   │   │   ├── topbar.css
│   │   │   ├── sidenav.css
│   │   │   ├── card.css                    # Glassmorphism card
│   │   │   ├── kpi-tile.css
│   │   │   ├── data-table.css
│   │   │   ├── status-badge.css            # SUCCESS / PENDING / FAILED / TIMEOUT
│   │   │   ├── timeline.css                # Journey state changes
│   │   │   ├── chart.css                   # Canvas chart frames
│   │   │   ├── modal.css
│   │   │   ├── toast.css
│   │   │   ├── tabs.css
│   │   │   ├── filter-bar.css
│   │   │   ├── loader.css                  # Skeletons + spinners
│   │   │   └── empty-state.css
│   │   │
│   │   ├── dashboards/
│   │   │   ├── executive.css
│   │   │   ├── live.css
│   │   │   ├── events.css
│   │   │   ├── journey.css
│   │   │   ├── kafka.css
│   │   │   ├── reconciliation.css
│   │   │   ├── ledger.css
│   │   │   ├── audit.css
│   │   │   ├── errors.css
│   │   │   ├── health.css
│   │   │   ├── architecture.css
│   │   │   └── demo.css
│   │   │
│   │   ├── themes/
│   │   │   └── dark-navy.css               # Active theme (purple/cyan accents)
│   │   │
│   │   └── main.css                        # Imports all of the above (single entry)
│   │
│   ├── js/
│   │   ├── core/
│   │   │   ├── config.js                   # API_BASE_URL, poll intervals, env flags
│   │   │   ├── router.js                   # Hash router (#/executive, …) + Outlet swap
│   │   │   ├── state.js                    # Global store (current route, filters, cache)
│   │   │   ├── eventBus.js                 # Pub/sub for cross-module events
│   │   │   ├── api.js                      # fetch() wrapper, retries, error mapping
│   │   │   ├── polling.js                  # Interval manager (start/stop per route)
│   │   │   ├── sse.js                      # Server-Sent Events client (event stream)
│   │   │   ├── formatter.js                # Currency, datetime, latency, UPI ID mask
│   │   │   ├── logger.js
│   │   │   └── constants.js                # Status enums, error categories, topics
│   │   │
│   │   ├── services/                       # One file per backend endpoint group
│   │   │   ├── healthService.js            # GET /api/v1/ops/health
│   │   │   ├── overviewService.js          # GET /api/v1/ops/overview
│   │   │   ├── transactionService.js       # /transactions/{tid}/journey, /search
│   │   │   ├── eventsService.js            # /events/stream
│   │   │   ├── kafkaService.js             # /kafka/status
│   │   │   ├── reconciliationService.js
│   │   │   ├── ledgerService.js
│   │   │   ├── auditService.js
│   │   │   └── errorsService.js
│   │   │
│   │   ├── components/                     # Reusable UI renderers
│   │   │   ├── topbar.js
│   │   │   ├── sidenav.js
│   │   │   ├── kpiTile.js
│   │   │   ├── dataTable.js
│   │   │   ├── statusBadge.js
│   │   │   ├── timeline.js
│   │   │   ├── chart.js                    # Canvas line/bar/donut (no library)
│   │   │   ├── modal.js
│   │   │   ├── toast.js
│   │   │   ├── tabs.js
│   │   │   ├── filterBar.js
│   │   │   ├── loader.js
│   │   │   └── emptyState.js
│   │   │
│   │   ├── dashboards/                     # One controller per route
│   │   │   ├── executive.js                # #/executive
│   │   │   ├── live.js                     # #/live
│   │   │   ├── events.js                   # #/events
│   │   │   ├── journey.js                  # #/journey
│   │   │   ├── kafka.js                    # #/kafka
│   │   │   ├── reconciliation.js           # #/reconciliation
│   │   │   ├── ledger.js                   # #/ledger
│   │   │   ├── audit.js                    # #/audit
│   │   │   ├── errors.js                   # #/errors
│   │   │   ├── health.js                   # #/health
│   │   │   ├── architecture.js             # #/architecture
│   │   │   └── demo.js                     # #/demo
│   │   │
│   │   ├── mock/
│   │   │   ├── mockApi.js                  # Toggleable mock layer for fetch()
│   │   │   ├── mockHealth.json
│   │   │   ├── mockTransactions.json
│   │   │   ├── mockEvents.json
│   │   │   ├── mockKafka.json
│   │   │   ├── mockReconciliation.json
│   │   │   ├── mockLedger.json
│   │   │   ├── mockAudit.json
│   │   │   └── mockErrors.json
│   │   │
│   │   └── main.js                         # Bootstraps: mounts shell, starts router
│   │
│   ├── img/
│   │   ├── logo.svg
│   │   ├── logo-mark.svg
│   │   ├── favicon.svg
│   │   └── illustrations/
│   │       ├── empty-state.svg
│   │       └── architecture-flow.svg
│   │
│   ├── icons/                              # Inline SVG icon set
│   │   ├── nav/                            # 12 sidenav icons (one per dashboard)
│   │   │   ├── executive.svg
│   │   │   ├── live.svg
│   │   │   ├── events.svg
│   │   │   ├── journey.svg
│   │   │   ├── kafka.svg
│   │   │   ├── reconciliation.svg
│   │   │   ├── ledger.svg
│   │   │   ├── audit.svg
│   │   │   ├── errors.svg
│   │   │   ├── health.svg
│   │   │   ├── architecture.svg
│   │   │   └── demo.svg
│   │   └── ui/                             # search, refresh, filter, close, etc.
│   │
│   └── fonts/
│       └── (self-hosted display + mono font files)
│
├── dashboards/                             # HTML partials loaded by router
│   ├── executive.html
│   ├── live.html
│   ├── events.html
│   ├── journey.html
│   ├── kafka.html
│   ├── reconciliation.html
│   ├── ledger.html
│   ├── audit.html
│   ├── errors.html
│   ├── health.html
│   ├── architecture.html
│   └── demo.html
│
├── docs/
│   ├── APEX-SUMMARY.md                     # Source of truth (copy)
│   ├── API-CONTRACTS.md                    # Endpoint shapes the UI expects
│   └── THEME.md                            # Color tokens, glass spec
│
└── README.md
```

Navigation hierarchy (hash router → `dashboards/*.html` partial → `assets/js/dashboards/*.js` controller → `assets/js/services/*.js` → Spring Boot `/api/v1/ops/*`):

```
#/executive       → Executive Overview         (healthService + overviewService)
#/live            → Live Transaction Monitor   (transactionService, polling)
#/events          → Real-Time Event Stream     (eventsService, SSE)
#/journey         → Transaction Journey        (transactionService.getJourney)
#/kafka           → Kafka Monitoring Center    (kafkaService)
#/reconciliation  → Reconciliation Dashboard   (reconciliationService)
#/ledger          → Ledger Dashboard           (ledgerService)
#/audit           → Audit Dashboard            (auditService)
#/errors          → Error Intelligence Center  (errorsService)
#/health          → Service Health Center      (healthService)
#/architecture    → Architecture Flow          (static)
#/demo            → Demo Mode                  (mockApi)
```

Standing by for the next instruction. got from lovable
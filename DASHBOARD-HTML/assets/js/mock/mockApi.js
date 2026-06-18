/**
 * Mock API layer — intercepts fetch for /api/v1/ops/* when USE_MOCK is enabled.
 * Import this module before any service calls (main.js loads it first).
 */

import { USE_MOCK } from "../core/config.js";

const MOCK_DELAY_MS = 180;

let installed = false;
let eventCounter = 0;

function delay(ms = MOCK_DELAY_MS) {
  return new Promise((r) => setTimeout(r, ms));
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function randomId(prefix = "TXN") {
  return `${prefix}${Date.now().toString(36).toUpperCase()}${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
}

function randomAmount() {
  return Math.floor(Math.random() * 50000) + 100;
}

const PAYERS = ["raj@okaxis", "priya@paytm", "amit@ybl", "sneha@ibl", "vikram@axl"];
const PAYEES = ["merchant@apex", "shop@upi", "vendor@biz", "store@pay"];

const SERVICES = [
  { name: "TPAP Ingress", status: "UP", latencyMs: 12 },
  { name: "Transaction Orchestrator", status: "UP", latencyMs: 28 },
  { name: "NPCI Adapter", status: "UP", latencyMs: 45 },
  { name: "Apache Kafka", status: "UP", latencyMs: 8 },
  { name: "Redis", status: "UP", latencyMs: 3 },
  { name: "PostgreSQL", status: "UP", latencyMs: 15 },
];

function buildVolumeSeries() {
  const labels = ["00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "Now"];
  return labels.map((label, i) => ({
    label,
    value: Math.round(800 + Math.sin(i * 0.9) * 400 + Math.random() * 200),
  }));
}

function buildTransactions(count = 25) {
  const statuses = ["SUCCESS", "SUCCESS", "SUCCESS", "FAILED", "PENDING"];
  return Array.from({ length: count }, (_, i) => {
    const status = statuses[Math.floor(Math.random() * statuses.length)];
    const ts = new Date(Date.now() - i * 45000 - Math.random() * 30000).toISOString();
    return {
      transactionId: randomId(),
      reference: `REF${100000 + i}`,
      payer: PAYERS[i % PAYERS.length],
      payee: PAYEES[i % PAYEES.length],
      amount: randomAmount(),
      status,
      timestamp: ts,
    };
  });
}

let cachedTransactions = buildTransactions(50);

function getOverview() {
  const tps = Math.round(1200 + Math.random() * 300);
  return {
    tps,
    successRate: 0.972 + Math.random() * 0.02,
    failureRate: 0.018 + Math.random() * 0.01,
    pendingCount: Math.floor(Math.random() * 45) + 5,
    p95LatencyMs: Math.round(180 + Math.random() * 80),
    timestamp: new Date().toISOString(),
    revenue: { total: Math.round(2840000 + Math.random() * 500000), delta: 4.2, currency: "INR", period: "Today" },
    volumeSeries: buildVolumeSeries(),
    healthStatus: "UP",
    services: SERVICES.map((s) => ({ ...s, latencyMs: s.latencyMs + Math.floor(Math.random() * 10) })),
  };
}

function getHealth() {
  return {
    status: "UP",
    timestamp: new Date().toISOString(),
    services: SERVICES.map((s) => ({ ...s, latencyMs: s.latencyMs + Math.floor(Math.random() * 10) })),
  };
}

function searchTransactions(url) {
  const params = new URL(url, window.location.origin).searchParams;
  const tr = params.get("tr")?.toLowerCase() ?? "";
  const pa = params.get("pa")?.toLowerCase() ?? "";
  const page = Math.max(1, parseInt(params.get("page") ?? "1", 10));
  const pageSize = Math.min(50, parseInt(params.get("pageSize") ?? "10", 10));
  const statusFilter = params.get("status")?.toUpperCase() ?? "";
  const txnId = params.get("transactionId")?.toLowerCase() ?? "";

  if (Math.random() > 0.7) {
    cachedTransactions = [...buildTransactions(5), ...cachedTransactions].slice(0, 60);
  }

  let filtered = cachedTransactions.filter((t) => {
    if (txnId && !t.transactionId.toLowerCase().includes(txnId)) return false;
    if (tr && !t.reference.toLowerCase().includes(tr)) return false;
    if (pa && !t.payer.toLowerCase().includes(pa) && !t.payee.toLowerCase().includes(pa)) return false;
    if (statusFilter && statusFilter !== "ALL" && t.status !== statusFilter) return false;
    return true;
  });

  const total = filtered.length;
  const start = (page - 1) * pageSize;
  const results = filtered.slice(start, start + pageSize);

  return { results, total, page, pageSize };
}

function getJourney(tid) {
  const states = [
    { state: "INITIATED", service: "TPAP Ingress", message: "Transaction received from TPAP" },
    { state: "VALIDATED", service: "Transaction Orchestrator", message: "Validation passed" },
    { state: "ROUTED", service: "Transaction Orchestrator", message: "Published to Kafka topic upi.txn.initiated" },
    { state: "NPCI_SENT", service: "NPCI Adapter", message: "Request sent to NPCI" },
    { state: "NPCI_ACK", service: "NPCI Adapter", message: "NPCI acknowledgment received" },
    { state: "COMPLETED", service: "Transaction Orchestrator", message: "Transaction settled successfully" },
  ];
  const base = Date.now() - 8000;
  return {
    transactionId: tid,
    status: "SUCCESS",
    startedAt: new Date(base).toISOString(),
    completedAt: new Date(base + 7500).toISOString(),
    events: states.map((s, i) => ({
      ...s,
      timestamp: new Date(base + i * 1200).toISOString(),
    })),
  };
}

function getEvents() {
  eventCounter += 1;
  const types = ["TXN_INIT", "TXN_COMPLETE", "TXN_FAIL", "KAFKA_LAG", "HEALTH_CHECK"];
  const services = ["tpap-ingress", "transaction-orchestrator", "npci-adapter"];
  const events = Array.from({ length: 15 }, (_, i) => ({
    id: `EVT-${eventCounter}-${i}`,
    type: types[Math.floor(Math.random() * types.length)],
    service: services[i % services.length],
    message: `Event payload received on topic upi.txn.${i % 2 ? "completed" : "initiated"}`,
    timestamp: new Date(Date.now() - i * 3200).toISOString(),
    severity: i % 7 === 0 ? "WARN" : "INFO",
  }));
  return { events, timestamp: new Date().toISOString() };
}

function getKafkaStatus() {
  return {
    timestamp: new Date().toISOString(),
    brokerStatus: "UP",
    topics: [
      { name: "upi.txn.initiated", partitions: 6, messagesPerSec: 420, lag: 0 },
      { name: "upi.txn.completed", partitions: 6, messagesPerSec: 415, lag: 2 },
      { name: "upi.txn.failed", partitions: 3, messagesPerSec: 12, lag: 0 },
      { name: "upi.callback.received", partitions: 3, messagesPerSec: 380, lag: 1 },
      { name: "ledger.entry.created", partitions: 3, messagesPerSec: 390, lag: 0 },
    ],
    consumerGroups: [
      { group: "transaction-orchestrator", topic: "upi.txn.initiated", lag: 0, members: 3 },
      { group: "npci-adapter", topic: "upi.txn.initiated", lag: 1, members: 2 },
      { group: "dashboard-monitor-group", topic: "upi.txn.completed", lag: 5, members: 1 },
    ],
  };
}

function getReconciliation() {
  return {
    timestamp: new Date().toISOString(),
    matched: 9842,
    mismatched: 3,
    pending: 12,
    mismatches: [
      { id: "REC-001", txnId: randomId(), expected: 1500, actual: 1500, issue: "Timing drift", status: "INVESTIGATING" },
      { id: "REC-002", txnId: randomId(), expected: 3200, actual: 0, issue: "Missing ledger entry", status: "OPEN" },
      { id: "REC-003", txnId: randomId(), expected: 890, actual: 890, issue: "Duplicate callback", status: "RESOLVED" },
    ],
  };
}

function getLedger() {
  return {
    timestamp: new Date().toISOString(),
    entries: Array.from({ length: 12 }, (_, i) => ({
      id: `LED-${1000 + i}`,
      txnId: randomId(),
      debit: i % 2 ? randomAmount() : 0,
      credit: i % 2 ? 0 : randomAmount(),
      balance: 1250000 + i * 1500,
      timestamp: new Date(Date.now() - i * 600000).toISOString(),
      description: i % 2 ? "UPI credit settlement" : "UPI debit settlement",
    })),
  };
}

function getAudit() {
  return {
    timestamp: new Date().toISOString(),
    entries: Array.from({ length: 10 }, (_, i) => ({
      id: `AUD-${500 + i}`,
      actor: "system@apex-upi",
      action: ["READ_TXN", "READ_HEALTH", "READ_LEDGER", "SEARCH_TXN"][i % 4],
      resource: `/api/v1/ops/${["transactions", "health", "ledger", "overview"][i % 4]}`,
      timestamp: new Date(Date.now() - i * 120000).toISOString(),
      ip: "10.0.1." + (10 + i),
    })),
  };
}

function getErrors() {
  return {
    timestamp: new Date().toISOString(),
    total: 47,
    byCategory: {
      validation: 12,
      kafka: 8,
      callback: 15,
      database: 7,
      security: 5,
    },
    recent: Array.from({ length: 8 }, (_, i) => ({
      id: `ERR-${200 + i}`,
      category: ["validation", "kafka", "callback", "database", "security"][i % 5],
      message: [
        "Invalid UPI VPA format",
        "Consumer lag exceeded threshold",
        "NPCI callback timeout",
        "Connection pool exhausted",
        "Invalid API token scope",
      ][i % 5],
      count: Math.floor(Math.random() * 20) + 1,
      lastSeen: new Date(Date.now() - i * 3600000).toISOString(),
    })),
  };
}

async function handleMockRequest(input, init) {
  const url = typeof input === "string" ? input : input.url;
  const method = (init?.method ?? "GET").toUpperCase();

  if (method !== "GET" || !url.includes("/api/v1/ops/")) {
    return null;
  }

  await delay();

  const path = new URL(url, window.location.origin).pathname;

  if (path === "/api/v1/ops/overview") return jsonResponse(getOverview());
  if (path === "/api/v1/ops/health") return jsonResponse(getHealth());
  if (path.startsWith("/api/v1/ops/transactions/search")) return jsonResponse(searchTransactions(url));
  if (path.match(/\/api\/v1\/ops\/transactions\/[^/]+\/journey/)) {
    const tid = path.split("/")[5];
    return jsonResponse(getJourney(tid));
  }
  if (path === "/api/v1/ops/events") return jsonResponse(getEvents());
  if (path === "/api/v1/ops/kafka/status") return jsonResponse(getKafkaStatus());
  if (path === "/api/v1/ops/reconciliation") return jsonResponse(getReconciliation());
  if (path === "/api/v1/ops/ledger") return jsonResponse(getLedger());
  if (path === "/api/v1/ops/audit") return jsonResponse(getAudit());
  if (path === "/api/v1/ops/errors") return jsonResponse(getErrors());

  return jsonResponse({ error: "Not found" }, 404);
}

export function installMockApi() {
  if (!USE_MOCK || installed) return;
  installed = true;

  const nativeFetch = window.fetch.bind(window);

  window.fetch = async (input, init) => {
    const mock = await handleMockRequest(input, init);
    if (mock) return mock;
    return nativeFetch(input, init);
  };

  console.info("[mockApi] Mock layer active — standalone demo mode");
}

installMockApi();

export default { installMockApi };

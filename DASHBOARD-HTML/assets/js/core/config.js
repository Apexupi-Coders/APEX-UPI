/**
 * Runtime configuration — API base URL, timeouts, polling intervals.
 * Override before bootstrap: window.__APEX_CONFIG__ = { BASE_URL: "http://..." }
 */

const runtime = typeof window !== "undefined" ? window.__APEX_CONFIG__ : null;

/** @type {string} Backend origin; empty string = same-origin relative paths */
export const BASE_URL = runtime?.BASE_URL ?? "";

/** @type {number} Default request timeout (ms) */
export const API_TIMEOUT_MS = runtime?.API_TIMEOUT_MS ?? 15_000;

/** @type {number} Max retry attempts for transient failures */
export const API_MAX_RETRIES = runtime?.API_MAX_RETRIES ?? 2;

/** @type {number} Executive overview health poll interval (ms) */
export const POLL_HEALTH_MS = runtime?.POLL_HEALTH_MS ?? 4_000;

/** @type {boolean} Enable verbose API logging */
export const API_DEBUG = runtime?.API_DEBUG ?? false;

/** @type {boolean} Use mock API layer (default true for standalone demo) */
export const USE_MOCK = runtime?.USE_MOCK ?? true;

/** @type {number} Live transaction poll interval (ms) */
export const POLL_LIVE_MS = runtime?.POLL_LIVE_MS ?? 5_000;

/** @type {number} Event stream poll interval (ms) */
export const POLL_EVENTS_MS = runtime?.POLL_EVENTS_MS ?? 3_000;

export const ENDPOINTS = Object.freeze({
  health: "/api/v1/ops/health",
  overview: "/api/v1/ops/overview",
  transactionJourney: (tid) => `/api/v1/ops/transactions/${encodeURIComponent(tid)}/journey`,
  transactionSearch: "/api/v1/ops/transactions/search",
  events: "/api/v1/ops/events",
  kafkaStatus: "/api/v1/ops/kafka/status",
  reconciliation: "/api/v1/ops/reconciliation",
  ledger: "/api/v1/ops/ledger",
  audit: "/api/v1/ops/audit",
  errors: "/api/v1/ops/errors",
});

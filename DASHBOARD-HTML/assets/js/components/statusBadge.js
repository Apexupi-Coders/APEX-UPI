import { escapeHtml } from "../core/formatter.js";

const STATUS_MAP = {
  SUCCESS: "success",
  UP: "success",
  HEALTHY: "success",
  OK: "success",
  PENDING: "pending",
  DEGRADED: "pending",
  WARN: "pending",
  WARNING: "pending",
  FAILED: "failed",
  DOWN: "failed",
  ERROR: "failed",
  TIMEOUT: "timeout",
};

export function statusBadgeClass(status) {
  return STATUS_MAP[String(status ?? "").toUpperCase()] ?? "neutral";
}

export function renderStatusBadge(status, { sm = false } = {}) {
  const cls = statusBadgeClass(status);
  const label = String(status ?? "UNKNOWN").toUpperCase();
  const smClass = sm ? " status-badge--sm" : "";
  return (
    `<span class="status-badge status-badge--${cls}${smClass}">` +
    `<span class="status-badge__dot"></span>` +
    `<span class="status-badge__label">${escapeHtml(label)}</span>` +
    `</span>`
  );
}

export default { statusBadgeClass, renderStatusBadge };

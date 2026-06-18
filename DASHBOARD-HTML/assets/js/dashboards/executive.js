/**
 * Executive Overview dashboard controller.
 * Data: overviewService (KPIs, volume, revenue) + health from overview metrics.
 */

import { getOverview } from "../services/overviewService.js";
import { POLL_HEALTH_MS } from "../core/config.js";

/** @type {number|null} */
let pollTimer = null;

/** @type {AbortController|null} */
let fetchController = null;

/** @type {HTMLElement|null} */
let root = null;

const HEALTH_STATUS_MAP = {
  UP: "success",
  HEALTHY: "success",
  OK: "success",
  DEGRADED: "pending",
  WARN: "pending",
  WARNING: "pending",
  DOWN: "failed",
  ERROR: "failed",
  FAILED: "failed",
};

/**
 * @param {number|null|undefined} value
 * @param {number} [decimals=0]
 */
function formatNumber(value, decimals = 0) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "—";
  }
  return Number(value).toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

/**
 * @param {number|null|undefined} value
 */
function formatPercent(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "—";
  }
  const num = Number(value);
  const normalized = num <= 1 && num >= 0 ? num * 100 : num;
  return `${normalized.toFixed(1)}%`;
}

/**
 * @param {number|null|undefined} amount
 * @param {string} [currency="INR"]
 */
function formatCurrency(amount, currency = "INR") {
  if (amount === null || amount === undefined || Number.isNaN(Number(amount))) {
    return "—";
  }
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 0,
    }).format(Number(amount));
  } catch {
    return `₹${formatNumber(amount)}`;
  }
}

/**
 * @param {string} iso
 */
function formatTimestamp(iso) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch {
    return iso;
  }
}

/**
 * Normalize API overview payload into dashboard shape.
 * @param {Record<string, unknown>} raw
 */
function normalizeOverview(raw) {
  const metrics = /** @type {Record<string, unknown>} */ (raw.metrics ?? {});

  const revenue = /** @type {Record<string, unknown>} */ (
    raw.revenue ?? metrics.revenue ?? {}
  );

  const volumeSeries =
    raw.volumeSeries ??
    metrics.volumeSeries ??
    raw.transactionVolume ??
    metrics.transactionVolume ??
    [];

  const healthServices =
    raw.services ??
    metrics.services ??
    raw.health?.services ??
    metrics.health?.services ??
    [];

  const healthStatus =
    raw.healthStatus ??
    metrics.healthStatus ??
    raw.health?.status ??
    metrics.health?.status ??
    null;

  return {
    tps: raw.tps ?? metrics.tps,
    successRate: raw.successRate ?? metrics.successRate,
    failureRate: raw.failureRate ?? metrics.failureRate,
    pendingCount: raw.pendingCount ?? metrics.pendingCount,
    p95LatencyMs: raw.p95LatencyMs ?? metrics.p95LatencyMs,
    timestamp: raw.timestamp ?? raw.generatedAt,
    revenueTotal: revenue.total ?? raw.revenueTotal ?? metrics.revenueTotal,
    revenueDelta: revenue.delta ?? raw.revenueDelta ?? metrics.revenueDelta,
    revenueCurrency: revenue.currency ?? "INR",
    revenuePeriod: revenue.period ?? "Today",
    volumeSeries: Array.isArray(volumeSeries) ? volumeSeries : [],
    healthStatus,
    healthServices: Array.isArray(healthServices) ? healthServices : [],
  };
}

/**
 * @param {HTMLElement} container
 * @param {string} kpi
 * @param {string} display
 * @param {{ delta?: string, deltaClass?: string, sub?: string }} [extra]
 */
function setKpi(container, kpi, display, extra = {}) {
  const card = container.querySelector(`[data-kpi="${kpi}"]`);
  if (!card) return;

  const valueEl = card.querySelector("[data-kpi-value]");
  const deltaEl = card.querySelector("[data-kpi-delta]");
  const subEl = card.querySelector("[data-kpi-sub]");

  if (valueEl) valueEl.textContent = display;
  card.dataset.loading = "false";

  if (deltaEl) {
    if (extra.delta) {
      deltaEl.textContent = extra.delta;
      deltaEl.className = `card__metric-delta ${extra.deltaClass ?? ""}`.trim();
      deltaEl.hidden = false;
    } else {
      deltaEl.hidden = true;
    }
  }

  if (subEl && extra.sub) {
    subEl.textContent = extra.sub;
  }
}

/**
 * @param {CanvasRenderingContext2D} ctx
 * @param {{ label?: string, value?: number, tps?: number }[]} series
 * @param {number} width
 * @param {number} height
 */
function drawVolumeChart(ctx, series, width, height) {
  const dpr = window.devicePixelRatio || 1;
  const canvas = ctx.canvas;
  canvas.width = width * dpr;
  canvas.height = height * dpr;
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  ctx.scale(dpr, dpr);

  const pad = { top: 16, right: 16, bottom: 32, left: 48 };
  const chartW = width - pad.left - pad.right;
  const chartH = height - pad.top - pad.bottom;

  ctx.clearRect(0, 0, width, height);

  const values = series.map((p) => Number(p.value ?? p.tps ?? 0));
  if (!values.length) return { peak: null };

  const maxVal = Math.max(...values, 1);
  const minVal = 0;

  const gridColor = "rgba(148, 163, 184, 0.12)";
  const lineColor = "#22d3ee";
  const fillTop = "rgba(34, 211, 238, 0.22)";
  const fillBottom = "rgba(34, 211, 238, 0.02)";
  const labelColor = "#64748b";

  ctx.strokeStyle = gridColor;
  ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i += 1) {
    const y = pad.top + (chartH / 4) * i;
    ctx.beginPath();
    ctx.moveTo(pad.left, y);
    ctx.lineTo(pad.left + chartW, y);
    ctx.stroke();

    const val = maxVal - (maxVal - minVal) * (i / 4);
    ctx.fillStyle = labelColor;
    ctx.font = "11px system-ui, sans-serif";
    ctx.textAlign = "right";
    ctx.fillText(Math.round(val).toLocaleString(), pad.left - 8, y + 4);
  }

  const points = values.map((val, i) => {
    const x = pad.left + (chartW / Math.max(values.length - 1, 1)) * i;
    const y = pad.top + chartH - ((val - minVal) / (maxVal - minVal)) * chartH;
    return { x, y, val };
  });

  const gradient = ctx.createLinearGradient(0, pad.top, 0, pad.top + chartH);
  gradient.addColorStop(0, fillTop);
  gradient.addColorStop(1, fillBottom);

  ctx.beginPath();
  ctx.moveTo(points[0].x, pad.top + chartH);
  points.forEach((p) => ctx.lineTo(p.x, p.y));
  ctx.lineTo(points[points.length - 1].x, pad.top + chartH);
  ctx.closePath();
  ctx.fillStyle = gradient;
  ctx.fill();

  ctx.beginPath();
  points.forEach((p, i) => {
    if (i === 0) ctx.moveTo(p.x, p.y);
    else ctx.lineTo(p.x, p.y);
  });
  ctx.strokeStyle = lineColor;
  ctx.lineWidth = 2;
  ctx.lineJoin = "round";
  ctx.stroke();

  points.forEach((p) => {
    ctx.beginPath();
    ctx.arc(p.x, p.y, 3, 0, Math.PI * 2);
    ctx.fillStyle = lineColor;
    ctx.fill();
  });

  const labelStep = Math.max(1, Math.floor(series.length / 6));
  ctx.fillStyle = labelColor;
  ctx.font = "10px system-ui, sans-serif";
  ctx.textAlign = "center";
  series.forEach((point, i) => {
    if (i % labelStep !== 0 && i !== series.length - 1) return;
    const label = String(point.label ?? point.time ?? "");
    const x = pad.left + (chartW / Math.max(values.length - 1, 1)) * i;
    ctx.fillText(label, x, height - 10);
  });

  const peak = Math.max(...values);
  return { peak };
}

/**
 * @param {HTMLElement} container
 * @param {{ label?: string, value?: number, tps?: number }[]} series
 */
function renderVolumeChart(container, series) {
  const canvas = container.querySelector("[data-volume-chart]");
  const emptyEl = container.querySelector("[data-chart-empty]");
  const peakEl = container.querySelector("[data-chart-peak]");

  if (!canvas) return;

  if (!series.length) {
    canvas.hidden = true;
    if (emptyEl) emptyEl.hidden = false;
    if (peakEl) peakEl.textContent = "Peak —";
    return;
  }

  canvas.hidden = false;
  if (emptyEl) emptyEl.hidden = true;

  const rect = canvas.parentElement?.getBoundingClientRect();
  const width = Math.max(rect?.width ?? 800, 320);
  const height = 280;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  const { peak } = drawVolumeChart(ctx, series, width, height);
  if (peakEl && peak !== null) {
    peakEl.textContent = `Peak ${formatNumber(peak)} TPS`;
  }
}

/**
 * @param {string} status
 */
function healthBadgeClass(status) {
  const key = String(status ?? "").toUpperCase();
  return HEALTH_STATUS_MAP[key] ?? "neutral";
}

/**
 * @param {HTMLElement} container
 * @param {string|null} overall
 * @param {{ name?: string, status?: string, latencyMs?: number, message?: string }[]} services
 */
function renderHealthSummary(container, overall, services) {
  const overallEl = container.querySelector("[data-health-overall]");
  const listEl = container.querySelector("[data-health-list]");
  const emptyEl = container.querySelector("[data-health-empty]");

  if (!listEl) return;

  if (!services.length && !overall) {
    listEl.innerHTML = "";
    if (emptyEl) emptyEl.hidden = false;
    return;
  }

  if (emptyEl) emptyEl.hidden = true;

  if (overallEl) {
    const cls = healthBadgeClass(overall);
    overallEl.className = `status-badge status-badge--${cls}`;
    const label = overallEl.querySelector(".status-badge__label");
    if (label) label.textContent = overall ? String(overall).toUpperCase() : "UNKNOWN";
  }

  listEl.innerHTML = services
    .map((svc) => {
      const name = svc.name ?? "Unknown";
      const status = String(svc.status ?? "UNKNOWN").toUpperCase();
      const badge = healthBadgeClass(status);
      const latency =
        svc.latencyMs !== undefined && svc.latencyMs !== null
          ? `<span class="executive__health-latency">${svc.latencyMs}ms</span>`
          : "";

      return (
        `<li class="executive__health-item">` +
        `<span class="executive__health-name">${escapeHtml(name)}</span>` +
        `<span class="executive__health-meta">` +
        latency +
        `<span class="status-badge status-badge--${badge} status-badge--sm">` +
        `<span class="status-badge__dot"></span>` +
        `<span class="status-badge__label">${escapeHtml(status)}</span>` +
        `</span>` +
        `</span>` +
        `</li>`
      );
    })
    .join("");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/**
 * @param {HTMLElement} container
 * @param {ReturnType<typeof normalizeOverview>} data
 */
function renderOverview(container, data) {
  setKpi(container, "tps", formatNumber(data.tps));

  setKpi(container, "successRate", formatPercent(data.successRate), {
    delta: data.p95LatencyMs ? `p95 ${formatNumber(data.p95LatencyMs)} ms` : undefined,
    deltaClass: "text-accent",
  });

  setKpi(container, "failureRate", formatPercent(data.failureRate));

  setKpi(container, "pendingCount", formatNumber(data.pendingCount), {
    deltaClass: "card__metric-delta--down",
  });

  const revenueDelta =
    data.revenueDelta !== undefined && data.revenueDelta !== null
      ? `${Number(data.revenueDelta) >= 0 ? "▲" : "▼"} ${Math.abs(Number(data.revenueDelta)).toFixed(1)}%`
      : undefined;

  setKpi(container, "revenue", formatCurrency(data.revenueTotal, data.revenueCurrency), {
    delta: revenueDelta,
    deltaClass:
      data.revenueDelta !== undefined && Number(data.revenueDelta) >= 0
        ? "card__metric-delta--up"
        : "card__metric-delta--down",
    sub: `${data.revenuePeriod} · ${data.revenueCurrency}`,
  });

  renderVolumeChart(container, data.volumeSeries);
  if (root) {
    root.dataset.volumeSeries = JSON.stringify(data.volumeSeries);
  }
  renderHealthSummary(container, data.healthStatus, data.healthServices);

  const updatedEl = container.querySelector("[data-executive-updated]");
  if (updatedEl) {
    updatedEl.textContent = data.timestamp
      ? `Updated ${formatTimestamp(data.timestamp)}`
      : `Updated ${formatTimestamp(new Date().toISOString())}`;
    updatedEl.dateTime = data.timestamp ?? new Date().toISOString();
  }
}

/**
 * @param {HTMLElement} container
 * @param {string|null} message
 */
function setError(container, message) {
  const errorEl = container.querySelector("[data-executive-error]");
  const statusEl = container.querySelector("[data-executive-status]");

  if (errorEl) {
    errorEl.textContent = message ?? "";
    errorEl.hidden = !message;
  }

  if (statusEl) {
    statusEl.textContent = message ? "Degraded" : "Live";
    statusEl.className = message ? "badge badge--failed" : "badge badge--success";
  }
}

/**
 * @param {HTMLElement} container
 */
function setLoading(container) {
  container.querySelectorAll("[data-kpi]").forEach((card) => {
    card.dataset.loading = "true";
  });

  const statusEl = container.querySelector("[data-executive-status]");
  if (statusEl) {
    statusEl.textContent = "Refreshing";
    statusEl.className = "badge badge--info";
  }
}

async function refresh() {
  if (!root) return;

  fetchController?.abort();
  fetchController = new AbortController();

  setLoading(root);

  try {
    const raw = await getOverview({ signal: fetchController.signal });
    const data = normalizeOverview(/** @type {Record<string, unknown>} */ (raw ?? {}));
    renderOverview(root, data);
    setError(root, null);
  } catch (err) {
    if (err?.name === "AbortError") return;

    const message =
      err?.message ?? "Failed to load overview metrics from the operations API.";
    setError(root, message);
    console.error("[executive]", err);
  }
}

function onResize() {
  if (!root) return;
  const canvas = root.querySelector("[data-volume-chart]");
  if (!canvas || canvas.hidden) return;
  const seriesAttr = root.dataset.volumeSeries;
  if (!seriesAttr) return;
  try {
    renderVolumeChart(root, JSON.parse(seriesAttr));
  } catch {
    /* ignore */
  }
}

/**
 * @param {HTMLElement} container
 */
export function mount(container) {
  root = container.querySelector("[data-dashboard='executive']") ?? container;

  refresh();
  pollTimer = window.setInterval(refresh, POLL_HEALTH_MS);
  window.addEventListener("resize", onResize);

  return root;
}

export function unmount() {
  window.removeEventListener("resize", onResize);
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  fetchController?.abort();
  fetchController = null;
  root = null;
}

export default { mount, unmount };

/** Shared formatters for currency, datetime, latency, UPI ID masking */

export function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export function formatNumber(value, decimals = 0) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  return Number(value).toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function formatPercent(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "—";
  const num = Number(value);
  const normalized = num <= 1 && num >= 0 ? num * 100 : num;
  return `${normalized.toFixed(1)}%`;
}

export function formatCurrency(amount, currency = "INR") {
  if (amount === null || amount === undefined || Number.isNaN(Number(amount))) return "—";
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

export function formatTimestamp(iso, opts = {}) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      ...opts,
    });
  } catch {
    return iso;
  }
}

export function formatLatency(ms) {
  if (ms === null || ms === undefined) return "—";
  const n = Number(ms);
  if (n >= 1000) return `${(n / 1000).toFixed(2)}s`;
  return `${Math.round(n)}ms`;
}

export function maskUpiId(id) {
  if (!id) return "—";
  const s = String(id);
  const at = s.indexOf("@");
  if (at <= 2) return s;
  return `${s.slice(0, 2)}***${s.slice(at)}`;
}

export default {
  escapeHtml,
  formatNumber,
  formatPercent,
  formatCurrency,
  formatTimestamp,
  formatLatency,
  maskUpiId,
};

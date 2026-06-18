/** Shared dashboard helpers */

import { escapeHtml, formatTimestamp, formatCurrency, maskUpiId } from "../core/formatter.js";
import { renderStatusBadge } from "../components/statusBadge.js";

export { escapeHtml, formatTimestamp, formatCurrency, maskUpiId, renderStatusBadge };

export function setError(root, selector, message) {
  const el = root.querySelector(selector);
  if (el) {
    el.textContent = message ?? "";
    el.hidden = !message;
  }
}

export function setBadge(root, selector, text, variant = "info") {
  const el = root.querySelector(selector);
  if (el) {
    el.textContent = text;
    el.className = `badge badge--${variant}`;
  }
}

export default { escapeHtml, setError, setBadge, renderStatusBadge };

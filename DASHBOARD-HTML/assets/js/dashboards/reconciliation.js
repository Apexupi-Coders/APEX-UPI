import { getReconciliation } from "../services/reconciliationService.js";
import { escapeHtml, formatCurrency, formatTimestamp, renderStatusBadge, setError } from "./shared.js";

let root = null;
let fetchController = null;

function renderSummary(data) {
  const fields = [
    ["matched", data.matched],
    ["mismatched", data.mismatched],
    ["pending", data.pending],
  ];
  fields.forEach(([key, val]) => {
    const el = root.querySelector(`[data-rec-${key}]`);
    if (el) el.textContent = val ?? "—";
  });
}

function renderMismatches(rows) {
  const tbody = root.querySelector("[data-rec-tbody]");
  const empty = root.querySelector("[data-rec-empty]");
  if (!tbody) return;

  if (!rows?.length) {
    tbody.innerHTML = "";
    if (empty) empty.hidden = false;
    return;
  }
  if (empty) empty.hidden = true;

  tbody.innerHTML = rows
    .map(
      (r) =>
        `<tr>` +
        `<td class="text-mono">${escapeHtml(r.id)}</td>` +
        `<td class="text-mono">${escapeHtml(r.txnId)}</td>` +
        `<td class="data-table__cell--numeric">${formatCurrency(r.expected)}</td>` +
        `<td class="data-table__cell--numeric">${formatCurrency(r.actual)}</td>` +
        `<td>${escapeHtml(r.issue)}</td>` +
        `<td>${renderStatusBadge(r.status, { sm: true })}</td>` +
        `</tr>`
    )
    .join("");
}

async function refresh() {
  if (!root) return;
  fetchController?.abort();
  fetchController = new AbortController();

  try {
    const data = await getReconciliation({ signal: fetchController.signal });
    renderSummary(data);
    renderMismatches(data.mismatches);
    const updated = root.querySelector("[data-rec-updated]");
    if (updated) updated.textContent = formatTimestamp(data.timestamp);
    setError(root, "[data-rec-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setError(root, "[data-rec-error]", err?.message ?? "Failed to load reconciliation data.");
  }
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='reconciliation']") ?? container;
  refresh();
  return root;
}

export function unmount() {
  fetchController?.abort();
  root = null;
}

export default { mount, unmount };

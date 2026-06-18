import { getLedger } from "../services/ledgerService.js";
import { escapeHtml, formatCurrency, formatTimestamp, setError } from "./shared.js";

let root = null;
let fetchController = null;

function renderEntries(entries) {
  const tbody = root.querySelector("[data-ledger-tbody]");
  const empty = root.querySelector("[data-ledger-empty]");
  if (!tbody) return;

  if (!entries?.length) {
    tbody.innerHTML = "";
    if (empty) empty.hidden = false;
    return;
  }
  if (empty) empty.hidden = true;

  tbody.innerHTML = entries
    .map(
      (e) =>
        `<tr>` +
        `<td class="text-mono">${escapeHtml(e.id)}</td>` +
        `<td class="text-mono">${escapeHtml(e.txnId)}</td>` +
        `<td class="data-table__cell--numeric">${e.debit ? formatCurrency(e.debit) : "—"}</td>` +
        `<td class="data-table__cell--numeric">${e.credit ? formatCurrency(e.credit) : "—"}</td>` +
        `<td class="data-table__cell--numeric">${formatCurrency(e.balance)}</td>` +
        `<td>${escapeHtml(e.description)}</td>` +
        `<td class="text-xs text-mono">${formatTimestamp(e.timestamp)}</td>` +
        `</tr>`
    )
    .join("");
}

async function refresh() {
  if (!root) return;
  fetchController?.abort();
  fetchController = new AbortController();

  try {
    const data = await getLedger({ signal: fetchController.signal });
    renderEntries(data.entries);
    const updated = root.querySelector("[data-ledger-updated]");
    if (updated) updated.textContent = formatTimestamp(data.timestamp);
    setError(root, "[data-ledger-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setError(root, "[data-ledger-error]", err?.message ?? "Failed to load ledger.");
  }
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='ledger']") ?? container;
  refresh();
  return root;
}

export function unmount() {
  fetchController?.abort();
  root = null;
}

export default { mount, unmount };

import { getAuditLog } from "../services/auditService.js";
import { escapeHtml, formatTimestamp, setError } from "./shared.js";

let root = null;
let fetchController = null;

function renderEntries(entries) {
  const tbody = root.querySelector("[data-audit-tbody]");
  const empty = root.querySelector("[data-audit-empty]");
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
        `<td>${escapeHtml(e.actor)}</td>` +
        `<td><span class="chip">${escapeHtml(e.action)}</span></td>` +
        `<td class="text-mono text-xs">${escapeHtml(e.resource)}</td>` +
        `<td class="text-mono text-xs">${escapeHtml(e.ip)}</td>` +
        `<td class="text-xs">${formatTimestamp(e.timestamp)}</td>` +
        `</tr>`
    )
    .join("");
}

async function refresh() {
  if (!root) return;
  fetchController?.abort();
  fetchController = new AbortController();

  try {
    const data = await getAuditLog({ signal: fetchController.signal });
    renderEntries(data.entries);
    const updated = root.querySelector("[data-audit-updated]");
    if (updated) updated.textContent = formatTimestamp(data.timestamp);
    setError(root, "[data-audit-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setError(root, "[data-audit-error]", err?.message ?? "Failed to load audit log.");
  }
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='audit']") ?? container;
  refresh();
  return root;
}

export function unmount() {
  fetchController?.abort();
  root = null;
}

export default { mount, unmount };

import { getErrors } from "../services/errorsService.js";
import { escapeHtml, formatNumber, formatTimestamp, setError } from "./shared.js";

let root = null;
let fetchController = null;

function renderCategories(byCategory) {
  const grid = root.querySelector("[data-errors-categories]");
  if (!grid || !byCategory) return;

  grid.innerHTML = Object.entries(byCategory)
    .map(
      ([cat, count]) =>
        `<article class="card card--kpi">` +
        `<div class="card__body">` +
        `<span class="card__metric-label">${escapeHtml(cat)}</span>` +
        `<span class="card__metric">${formatNumber(count)}</span>` +
        `</div></article>`
    )
    .join("");
}

function renderRecent(rows) {
  const tbody = root.querySelector("[data-errors-tbody]");
  const empty = root.querySelector("[data-errors-empty]");
  if (!tbody) return;

  if (!rows?.length) {
    tbody.innerHTML = "";
    if (empty) empty.hidden = false;
    return;
  }
  if (empty) empty.hidden = true;

  tbody.innerHTML = rows
    .map(
      (e) =>
        `<tr>` +
        `<td class="text-mono">${escapeHtml(e.id)}</td>` +
        `<td><span class="chip chip--accent">${escapeHtml(e.category)}</span></td>` +
        `<td>${escapeHtml(e.message)}</td>` +
        `<td class="data-table__cell--numeric">${e.count}</td>` +
        `<td class="text-xs">${formatTimestamp(e.lastSeen)}</td>` +
        `</tr>`
    )
    .join("");
}

async function refresh() {
  if (!root) return;
  fetchController?.abort();
  fetchController = new AbortController();

  try {
    const data = await getErrors({ signal: fetchController.signal });
    const totalEl = root.querySelector("[data-errors-total]");
    if (totalEl) totalEl.textContent = formatNumber(data.total);
    renderCategories(data.byCategory);
    renderRecent(data.recent);
    const updated = root.querySelector("[data-errors-updated]");
    if (updated) updated.textContent = formatTimestamp(data.timestamp);
    setError(root, "[data-errors-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setError(root, "[data-errors-error]", err?.message ?? "Failed to load errors.");
  }
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='errors']") ?? container;
  refresh();
  return root;
}

export function unmount() {
  fetchController?.abort();
  root = null;
}

export default { mount, unmount };

/**
 * Live Transaction Monitor controller.
 */

import { searchTransactions } from "../services/transactionService.js";
import { POLL_LIVE_MS } from "../core/config.js";
import {
  escapeHtml,
  formatTimestamp,
  formatCurrency,
  maskUpiId,
  renderStatusBadge,
  setError,
  setBadge,
} from "./shared.js";

let root = null;
let pollTimer = null;
let fetchController = null;
let page = 1;
let pageSize = 10;
let statusFilter = "all";
let searchQuery = "";

function renderRows(transactions) {
  const tbody = root.querySelector("[data-live-tbody]");
  const emptyEl = root.querySelector("[data-live-empty]");
  const table = root.querySelector("[data-live-table]");

  if (!tbody) return;

  if (!transactions.length) {
    tbody.innerHTML = "";
    if (emptyEl) emptyEl.hidden = false;
    if (table) table.classList.add("data-table--empty");
    return;
  }

  if (emptyEl) emptyEl.hidden = true;
  if (table) table.classList.remove("data-table--empty");

  tbody.innerHTML = transactions
    .map(
      (t) =>
        `<tr>` +
        `<td class="text-mono">${escapeHtml(t.transactionId)}</td>` +
        `<td>${escapeHtml(t.reference ?? "—")}</td>` +
        `<td>${escapeHtml(maskUpiId(t.payer))}</td>` +
        `<td>${escapeHtml(maskUpiId(t.payee))}</td>` +
        `<td class="data-table__cell--numeric">${formatCurrency(t.amount)}</td>` +
        `<td>${renderStatusBadge(t.status, { sm: true })}</td>` +
        `<td class="text-mono text-xs">${formatTimestamp(t.timestamp)}</td>` +
        `</tr>`
    )
    .join("");
}

function updatePagination(total) {
  const meta = root.querySelector("[data-live-page-meta]");
  const prevBtn = root.querySelector("[data-live-prev]");
  const nextBtn = root.querySelector("[data-live-next]");
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const start = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, total);

  if (meta) meta.textContent = `Showing ${start}–${end} of ${total}`;
  if (prevBtn) prevBtn.disabled = page <= 1;
  if (nextBtn) nextBtn.disabled = page >= totalPages;
}

function updateTimestamp() {
  const el = root.querySelector("[data-live-updated]");
  if (el) {
    const now = new Date().toISOString();
    el.textContent = `Updated ${formatTimestamp(now)}`;
    el.dateTime = now;
  }
}

async function refresh() {
  if (!root) return;

  fetchController?.abort();
  fetchController = new AbortController();

  setBadge(root, "[data-live-status]", "Refreshing", "info");

  try {
    const params = { page, pageSize };
    if (searchQuery) params.transactionId = searchQuery;
    if (statusFilter !== "all") params.status = statusFilter.toUpperCase();

    const data = await searchTransactions(params, { signal: fetchController.signal });
    renderRows(data.results ?? []);
    updatePagination(data.total ?? 0);
    updateTimestamp();
    setBadge(root, "[data-live-status]", "Live", "success");
    setError(root, "[data-live-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setBadge(root, "[data-live-status]", "Error", "failed");
    setError(root, "[data-live-error]", err?.message ?? "Failed to load transactions.");
  }
}

function bindEvents() {
  const searchInput = root.querySelector("[data-live-search]");
  const filters = root.querySelector("[data-live-filters]");
  const prevBtn = root.querySelector("[data-live-prev]");
  const nextBtn = root.querySelector("[data-live-next]");

  let debounce;
  searchInput?.addEventListener("input", (e) => {
    clearTimeout(debounce);
    debounce = setTimeout(() => {
      searchQuery = e.target.value.trim();
      page = 1;
      refresh();
    }, 300);
  });

  filters?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-live-filter]");
    if (!btn) return;
    statusFilter = btn.dataset.liveFilter;
    page = 1;
    filters.querySelectorAll("[data-live-filter]").forEach((b) => {
      const active = b === btn;
      b.classList.toggle("chip--active", active);
      b.setAttribute("aria-pressed", active ? "true" : "false");
    });
    refresh();
  });

  prevBtn?.addEventListener("click", () => {
    if (page > 1) {
      page -= 1;
      refresh();
    }
  });

  nextBtn?.addEventListener("click", () => {
    page += 1;
    refresh();
  });
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='live']") ?? container;
  page = 1;
  bindEvents();
  refresh();
  pollTimer = window.setInterval(refresh, POLL_LIVE_MS);
  return root;
}

export function unmount() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  fetchController?.abort();
  fetchController = null;
  root = null;
}

export default { mount, unmount };

/**
 * Demo Mode — showcases mock playback with speed control.
 */

import { getOverview } from "../services/overviewService.js";
import { searchTransactions } from "../services/transactionService.js";
import { escapeHtml, formatNumber, formatPercent, formatTimestamp, setError } from "./shared.js";

let root = null;
let pollTimer = null;
let speed = 1;

async function tick() {
  if (!root) return;

  try {
    const [overview, txns] = await Promise.all([
      getOverview(),
      searchTransactions({ page: 1, pageSize: 5 }),
    ]);

    const kpiEl = root.querySelector("[data-demo-kpis]");
    if (kpiEl) {
      kpiEl.innerHTML =
        `<span class="chip chip--active">TPS ${formatNumber(overview.tps)}</span>` +
        `<span class="chip">Success ${formatPercent(overview.successRate)}</span>` +
        `<span class="chip">Pending ${formatNumber(overview.pendingCount)}</span>`;
    }

    const feed = root.querySelector("[data-demo-feed]");
    if (feed) {
      feed.innerHTML = (txns.results ?? [])
        .map(
          (t) =>
            `<li class="demo__feed-item">` +
            `<span class="text-mono">${escapeHtml(t.transactionId)}</span>` +
            `<span class="badge badge--${t.status === "SUCCESS" ? "success" : t.status === "FAILED" ? "failed" : "pending"}">${escapeHtml(t.status)}</span>` +
            `<time class="text-xs text-muted">${formatTimestamp(t.timestamp)}</time>` +
            `</li>`
        )
        .join("");
    }

    const updated = root.querySelector("[data-demo-updated]");
    if (updated) updated.textContent = formatTimestamp(new Date().toISOString());
    setError(root, "[data-demo-error]", null);
  } catch (err) {
    setError(root, "[data-demo-error]", err?.message ?? "Demo playback error.");
  }
}

function restartPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = window.setInterval(tick, Math.round(4000 / speed));
}

function bindEvents() {
  root.querySelector("[data-demo-speed]")?.addEventListener("change", (e) => {
    speed = parseFloat(e.target.value) || 1;
    restartPolling();
  });

  root.querySelector("[data-demo-refresh]")?.addEventListener("click", tick);
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='demo']") ?? container;
  bindEvents();
  tick();
  restartPolling();
  return root;
}

export function unmount() {
  if (pollTimer) clearInterval(pollTimer);
  root = null;
}

export default { mount, unmount };

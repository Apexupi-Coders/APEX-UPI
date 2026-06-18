import { getHealth } from "../services/healthService.js";
import { POLL_HEALTH_MS } from "../core/config.js";
import { escapeHtml, formatTimestamp, renderStatusBadge, setError, setBadge } from "./shared.js";

let root = null;
let pollTimer = null;
let fetchController = null;

function renderServices(services) {
  const grid = root.querySelector("[data-health-grid]");
  const empty = root.querySelector("[data-health-empty]");
  if (!grid) return;

  if (!services?.length) {
    grid.innerHTML = "";
    if (empty) empty.hidden = false;
    return;
  }
  if (empty) empty.hidden = true;

  grid.innerHTML = services
    .map(
      (s) =>
        `<article class="card card--compact health__card">` +
        `<div class="card__body health__card-body">` +
        `<div class="health__card-header">` +
        `<h4 class="health__card-name">${escapeHtml(s.name)}</h4>` +
        `${renderStatusBadge(s.status, { sm: true })}` +
        `</div>` +
        `<p class="health__card-latency text-mono text-sm text-muted">${s.latencyMs ?? "—"}ms latency</p>` +
        `</div></article>`
    )
    .join("");
}

async function refresh() {
  if (!root) return;
  fetchController?.abort();
  fetchController = new AbortController();

  try {
    const data = await getHealth({ signal: fetchController.signal });
    const overall = root.querySelector("[data-health-overall]");
    if (overall) overall.innerHTML = renderStatusBadge(data.status);
    renderServices(data.services);
    const updated = root.querySelector("[data-health-updated]");
    if (updated) updated.textContent = formatTimestamp(data.timestamp);
    setBadge(root, "[data-health-status]", "Monitoring", "success");
    setError(root, "[data-health-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setBadge(root, "[data-health-status]", "Error", "failed");
    setError(root, "[data-health-error]", err?.message ?? "Failed to load health.");
  }
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='health']") ?? container;
  refresh();
  pollTimer = window.setInterval(refresh, POLL_HEALTH_MS);
  return root;
}

export function unmount() {
  if (pollTimer) clearInterval(pollTimer);
  fetchController?.abort();
  root = null;
}

export default { mount, unmount };

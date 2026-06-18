import { getEvents } from "../services/eventsService.js";
import { POLL_EVENTS_MS } from "../core/config.js";
import { escapeHtml, formatTimestamp, setError, setBadge } from "./shared.js";

let root = null;
let pollTimer = null;
let fetchController = null;

function renderEvents(events) {
  const list = root.querySelector("[data-events-list]");
  const empty = root.querySelector("[data-events-empty]");
  if (!list) return;

  if (!events?.length) {
    list.innerHTML = "";
    if (empty) empty.hidden = false;
    return;
  }
  if (empty) empty.hidden = true;

  list.innerHTML = events
    .map(
      (ev) =>
        `<li class="timeline__item" data-severity="${escapeHtml(ev.severity ?? "INFO")}">` +
        `<div class="timeline__marker"></div>` +
        `<div class="timeline__content">` +
        `<div class="timeline__header">` +
        `<span class="timeline__type text-mono">${escapeHtml(ev.type)}</span>` +
        `<time class="timeline__time text-xs text-muted">${formatTimestamp(ev.timestamp)}</time>` +
        `</div>` +
        `<p class="timeline__message">${escapeHtml(ev.message)}</p>` +
        `<span class="chip chip--accent text-xs">${escapeHtml(ev.service)}</span>` +
        `</div></li>`
    )
    .join("");
}

async function refresh() {
  if (!root) return;
  fetchController?.abort();
  fetchController = new AbortController();
  setBadge(root, "[data-events-status]", "Streaming", "info");

  try {
    const data = await getEvents({ signal: fetchController.signal });
    renderEvents(data.events ?? []);
    const updated = root.querySelector("[data-events-updated]");
    if (updated) updated.textContent = `Updated ${formatTimestamp(data.timestamp)}`;
    setBadge(root, "[data-events-status]", "Live", "success");
    setError(root, "[data-events-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setBadge(root, "[data-events-status]", "Error", "failed");
    setError(root, "[data-events-error]", err?.message ?? "Failed to load events.");
  }
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='events']") ?? container;
  refresh();
  pollTimer = window.setInterval(refresh, POLL_EVENTS_MS);
  return root;
}

export function unmount() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = null;
  fetchController?.abort();
  root = null;
}

export default { mount, unmount };

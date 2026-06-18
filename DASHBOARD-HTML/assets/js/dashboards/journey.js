import { getJourney } from "../services/transactionService.js";
import { escapeHtml, formatTimestamp, renderStatusBadge, setError } from "./shared.js";

let root = null;
let fetchController = null;

function renderTimeline(events) {
  const list = root.querySelector("[data-journey-timeline]");
  const empty = root.querySelector("[data-journey-empty]");
  if (!list) return;

  if (!events?.length) {
    list.innerHTML = "";
    if (empty) empty.hidden = false;
    return;
  }
  if (empty) empty.hidden = true;

  list.innerHTML = events
    .map(
      (ev, i) =>
        `<li class="timeline__item timeline__item--${i === events.length - 1 ? "active" : ""}">` +
        `<div class="timeline__marker"></div>` +
        `<div class="timeline__content">` +
        `<div class="timeline__header">` +
        `<span class="timeline__type">${escapeHtml(ev.state)}</span>` +
        `<time class="timeline__time text-xs text-muted">${formatTimestamp(ev.timestamp)}</time>` +
        `</div>` +
        `<p class="timeline__message">${escapeHtml(ev.message ?? "")}</p>` +
        `<span class="chip text-xs">${escapeHtml(ev.service ?? "")}</span>` +
        `</div></li>`
    )
    .join("");
}

function renderMeta(data) {
  const statusEl = root.querySelector("[data-journey-status-badge]");
  const tidEl = root.querySelector("[data-journey-tid]");
  const started = root.querySelector("[data-journey-started]");
  const completed = root.querySelector("[data-journey-completed]");

  if (tidEl) tidEl.textContent = data.transactionId ?? "—";
  if (statusEl) statusEl.outerHTML = renderStatusBadge(data.status);
  if (started) started.textContent = formatTimestamp(data.startedAt);
  if (completed) completed.textContent = formatTimestamp(data.completedAt);
}

async function lookup(tid) {
  if (!root || !tid) return;

  fetchController?.abort();
  fetchController = new AbortController();
  setError(root, "[data-journey-error]", null);

  try {
    const data = await getJourney(tid, { signal: fetchController.signal });
    renderMeta(data);
    renderTimeline(data.events ?? []);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setError(root, "[data-journey-error]", err?.message ?? "Journey not found.");
  }
}

function bindEvents() {
  const form = root.querySelector("[data-journey-form]");
  form?.addEventListener("submit", (e) => {
    e.preventDefault();
    const input = root.querySelector("[data-journey-input]");
    lookup(input?.value.trim());
  });

  document.addEventListener("apex:search", (e) => {
    const q = e.detail?.query?.trim();
    if (q && root?.isConnected) {
      const input = root.querySelector("[data-journey-input]");
      if (input) input.value = q;
      lookup(q);
    }
  });
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='journey']") ?? container;
  bindEvents();
  lookup("TXNDEMO001");
  return root;
}

export function unmount() {
  fetchController?.abort();
  root = null;
}

export default { mount, unmount };

import { getKafkaStatus } from "../services/kafkaService.js";
import { POLL_HEALTH_MS } from "../core/config.js";
import { escapeHtml, formatNumber, formatTimestamp, renderStatusBadge, setError, setBadge } from "./shared.js";

let root = null;
let pollTimer = null;
let fetchController = null;

function renderTopics(topics) {
  const tbody = root.querySelector("[data-kafka-topics]");
  if (!tbody) return;
  tbody.innerHTML = (topics ?? [])
    .map(
      (t) =>
        `<tr>` +
        `<td class="text-mono">${escapeHtml(t.name)}</td>` +
        `<td class="data-table__cell--numeric">${t.partitions}</td>` +
        `<td class="data-table__cell--numeric">${formatNumber(t.messagesPerSec)}</td>` +
        `<td class="data-table__cell--numeric">${t.lag}</td>` +
        `</tr>`
    )
    .join("");
}

function renderGroups(groups) {
  const tbody = root.querySelector("[data-kafka-groups]");
  if (!tbody) return;
  tbody.innerHTML = (groups ?? [])
    .map(
      (g) =>
        `<tr>` +
        `<td class="text-mono">${escapeHtml(g.group)}</td>` +
        `<td>${escapeHtml(g.topic)}</td>` +
        `<td class="data-table__cell--numeric">${g.lag}</td>` +
        `<td class="data-table__cell--numeric">${g.members}</td>` +
        `</tr>`
    )
    .join("");
}

async function refresh() {
  if (!root) return;
  fetchController?.abort();
  fetchController = new AbortController();

  try {
    const data = await getKafkaStatus({ signal: fetchController.signal });
    renderTopics(data.topics);
    renderGroups(data.consumerGroups);
    const broker = root.querySelector("[data-kafka-broker]");
    if (broker) broker.innerHTML = renderStatusBadge(data.brokerStatus);
    const updated = root.querySelector("[data-kafka-updated]");
    if (updated) updated.textContent = formatTimestamp(data.timestamp);
    setBadge(root, "[data-kafka-status]", "Connected", "success");
    setError(root, "[data-kafka-error]", null);
  } catch (err) {
    if (err?.name === "AbortError") return;
    setBadge(root, "[data-kafka-status]", "Error", "failed");
    setError(root, "[data-kafka-error]", err?.message ?? "Failed to load Kafka status.");
  }
}

export function mount(container) {
  root = container.querySelector("[data-dashboard='kafka']") ?? container;
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

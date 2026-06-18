/**
 * Health service — GET /api/v1/ops/health
 * Aggregated service health (TPAP, Orchestrator, NPCI Adapter, Kafka, Redis, PostgreSQL).
 */

import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

/**
 * @typedef {object} ServiceHealth
 * @property {string} name
 * @property {string} status — UP | DOWN | DEGRADED
 * @property {string} [message]
 * @property {number} [latencyMs]
 */

/**
 * @typedef {object} HealthResponse
 * @property {string} status — overall UP | DOWN | DEGRADED
 * @property {string} [timestamp]
 * @property {ServiceHealth[]} [services]
 */

/**
 * Fetch aggregated operations health.
 * @param {import('../core/api.js').ApiRequestOptions} [options]
 * @returns {Promise<HealthResponse>}
 */
export function getHealth(options) {
  return api.get(ENDPOINTS.health, options);
}

export default { getHealth };

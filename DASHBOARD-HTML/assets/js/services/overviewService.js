/**
 * Overview service — GET /api/v1/ops/overview
 * Executive KPIs: TPS, success/failure rates, pending count, p95 latency.
 */

import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

/**
 * @typedef {object} OverviewResponse
 * @property {number} [tps]
 * @property {number} [successRate]
 * @property {number} [failureRate]
 * @property {number} [pendingCount]
 * @property {number} [p95LatencyMs]
 * @property {string} [timestamp]
 * @property {Record<string, unknown>} [metrics]
 */

/**
 * Fetch executive overview metrics.
 * @param {import('../core/api.js').ApiRequestOptions} [options]
 * @returns {Promise<OverviewResponse>}
 */
export function getOverview(options) {
  return api.get(ENDPOINTS.overview, options);
}

export default { getOverview };

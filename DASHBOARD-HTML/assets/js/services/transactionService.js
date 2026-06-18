/**
 * Transaction service — journey timeline and search.
 * GET /api/v1/ops/transactions/{tid}/journey
 * GET /api/v1/ops/transactions/search?tr=&pa=
 */

import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

/**
 * @typedef {object} JourneyEvent
 * @property {string} state
 * @property {string} [timestamp]
 * @property {string} [service]
 * @property {string} [message]
 * @property {Record<string, unknown>} [metadata]
 */

/**
 * @typedef {object} JourneyResponse
 * @property {string} transactionId
 * @property {string} [status]
 * @property {JourneyEvent[]} [events]
 * @property {string} [startedAt]
 * @property {string} [completedAt]
 */

/**
 * @typedef {object} TransactionSummary
 * @property {string} transactionId
 * @property {string} [reference]
 * @property {string} [payer]
 * @property {string} [payee]
 * @property {number} [amount]
 * @property {string} [status]
 * @property {string} [timestamp]
 */

/**
 * @typedef {object} TransactionSearchResponse
 * @property {TransactionSummary[]} [results]
 * @property {number} [total]
 * @property {number} [page]
 * @property {number} [pageSize]
 */

/**
 * @param {string} transactionId
 * @param {import('../core/api.js').ApiRequestOptions} [options]
 * @returns {Promise<JourneyResponse>}
 */
export function getJourney(transactionId, options) {
  const tid = String(transactionId ?? "").trim();
  if (!tid) {
    return Promise.reject(new Error("[transactionService] transactionId is required"));
  }

  return api.get(ENDPOINTS.transactionJourney(tid), options);
}

/**
 * @param {object} [query]
 * @param {string} [query.tr] — transaction reference
 * @param {string} [query.pa] — payer / payee UPI ID
 * @param {number} [query.page]
 * @param {number} [query.pageSize]
 * @param {import('../core/api.js').ApiRequestOptions} [options]
 * @returns {Promise<TransactionSearchResponse>}
 */
export function searchTransactions(query = {}, options = {}) {
  const { tr, pa, page, pageSize, ...rest } = query;

  return api.get(ENDPOINTS.transactionSearch, {
    ...options,
    params: {
      tr,
      pa,
      page,
      pageSize,
      ...rest,
    },
  });
}

export default { getJourney, searchTransactions };

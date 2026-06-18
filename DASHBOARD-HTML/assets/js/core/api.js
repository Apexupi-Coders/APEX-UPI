/**
 * fetch() wrapper — BASE_URL resolution, retries, timeout, error mapping.
 * Read-only GET-only dashboard API; no mutation methods exposed.
 */

import {
  BASE_URL,
  API_TIMEOUT_MS,
  API_MAX_RETRIES,
  API_DEBUG,
} from "./config.js";

const RETRYABLE_STATUS = new Set([408, 429, 500, 502, 503, 504]);

/**
 * @typedef {object} ApiRequestOptions
 * @property {Record<string, string|number|boolean|undefined|null>} [params]
 * @property {Record<string, string>} [headers]
 * @property {number} [timeout]
 * @property {number} [retries]
 * @property {AbortSignal} [signal]
 * @property {boolean} [parseJson] — default true
 */

export class ApiError extends Error {
  /**
   * @param {string} message
   * @param {object} [detail]
   * @param {number} [detail.status]
   * @param {string} [detail.statusText]
   * @param {string} [detail.url]
   * @param {unknown} [detail.body]
   * @param {string} [detail.code]
   */
  constructor(message, detail = {}) {
    super(message);
    this.name = "ApiError";
    this.status = detail.status ?? 0;
    this.statusText = detail.statusText ?? "";
    this.url = detail.url ?? "";
    this.body = detail.body ?? null;
    this.code = detail.code ?? mapStatusToCode(this.status);
  }
}

/**
 * @param {number} status
 * @returns {string}
 */
function mapStatusToCode(status) {
  if (status === 0) return "NETWORK_ERROR";
  if (status === 400) return "BAD_REQUEST";
  if (status === 401) return "UNAUTHORIZED";
  if (status === 403) return "FORBIDDEN";
  if (status === 404) return "NOT_FOUND";
  if (status === 408) return "TIMEOUT";
  if (status === 429) return "RATE_LIMITED";
  if (status >= 500) return "SERVER_ERROR";
  return "HTTP_ERROR";
}

/**
 * @param {string} message
 * @param {number} status
 * @returns {string}
 */
function mapErrorMessage(message, status) {
  if (status === 404) return "The requested resource was not found.";
  if (status === 429) return "Too many requests. Please try again shortly.";
  if (status >= 500) return "The operations API is temporarily unavailable.";
  if (status === 0) return message || "Unable to reach the operations API.";
  return message || `Request failed with status ${status}.`;
}

/**
 * @param {string} path
 * @param {Record<string, string|number|boolean|undefined|null>} [params]
 * @returns {string}
 */
export function buildUrl(path, params) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const base = BASE_URL.replace(/\/$/, "");
  const url = new URL(`${base}${normalizedPath}`, base || window.location.origin);

  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    });
  }

  return url.toString();
}

/**
 * @param {Response} response
 * @param {string} url
 * @returns {Promise<unknown>}
 */
async function parseResponseBody(response, url) {
  const contentType = response.headers.get("content-type") ?? "";
  const text = await response.text();

  if (!text) return null;

  if (contentType.includes("application/json") || text.startsWith("{") || text.startsWith("[")) {
    try {
      return JSON.parse(text);
    } catch {
      throw new ApiError("Invalid JSON response from API.", {
        status: response.status,
        statusText: response.statusText,
        url,
        body: text,
        code: "PARSE_ERROR",
      });
    }
  }

  return text;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isRetryableError(error, status) {
  if (error?.name === "AbortError") return false;
  if (status && RETRYABLE_STATUS.has(status)) return true;
  if (status === 0) return true;
  return false;
}

/**
 * @param {string} path
 * @param {ApiRequestOptions & { method?: string }} [options]
 * @returns {Promise<unknown>}
 */
export async function request(path, options = {}) {
  const {
    method = "GET",
    params,
    headers = {},
    timeout = API_TIMEOUT_MS,
    retries = API_MAX_RETRIES,
    signal,
    parseJson = true,
  } = options;

  const url = buildUrl(path, params);
  let lastError = null;

  for (let attempt = 0; attempt <= retries; attempt += 1) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    const onAbort = () => controller.abort();
    signal?.addEventListener("abort", onAbort);

    try {
      if (API_DEBUG) {
        console.debug(`[api] ${method} ${url} (attempt ${attempt + 1})`);
      }

      const response = await fetch(url, {
        method,
        headers: {
          Accept: "application/json",
          ...headers,
        },
        signal: controller.signal,
        credentials: "same-origin",
      });

      const body = parseJson ? await parseResponseBody(response, url) : await response.text();

      if (!response.ok) {
        const message =
          (body && typeof body === "object" && (body.message || body.error)) ||
          response.statusText ||
          "Request failed";

        const apiError = new ApiError(mapErrorMessage(String(message), response.status), {
          status: response.status,
          statusText: response.statusText,
          url,
          body,
        });

        if (attempt < retries && isRetryableError(null, response.status)) {
          lastError = apiError;
          await sleep(300 * 2 ** attempt);
          continue;
        }

        throw apiError;
      }

      return body;
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }

      const isAbort = error?.name === "AbortError";
      const apiError = new ApiError(
        isAbort ? "Request timed out." : error?.message || "Network request failed.",
        {
          status: isAbort ? 408 : 0,
          statusText: isAbort ? "Timeout" : "Network Error",
          url,
          code: isAbort ? "TIMEOUT" : "NETWORK_ERROR",
        }
      );

      if (attempt < retries && isRetryableError(error, 0)) {
        lastError = apiError;
        await sleep(300 * 2 ** attempt);
        continue;
      }

      throw apiError;
    } finally {
      clearTimeout(timeoutId);
      signal?.removeEventListener("abort", onAbort);
    }
  }

  throw lastError ?? new ApiError("Request failed after retries.", { url, status: 0 });
}

/**
 * @param {string} path
 * @param {ApiRequestOptions} [options]
 */
export function get(path, options) {
  return request(path, { ...options, method: "GET" });
}

export const api = Object.freeze({
  request,
  get,
  buildUrl,
  ApiError,
});

export default api;

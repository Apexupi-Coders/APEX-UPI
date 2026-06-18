/** Status enums and shared constants */

export const TXN_STATUS = Object.freeze({
  SUCCESS: "SUCCESS",
  FAILED: "FAILED",
  PENDING: "PENDING",
  TIMEOUT: "TIMEOUT",
});

export const HEALTH_STATUS = Object.freeze({
  UP: "UP",
  DOWN: "DOWN",
  DEGRADED: "DEGRADED",
});

export const ERROR_CATEGORIES = Object.freeze([
  "validation",
  "kafka",
  "callback",
  "database",
  "security",
]);

export const KAFKA_TOPICS = Object.freeze([
  "upi.txn.initiated",
  "upi.txn.completed",
  "upi.txn.failed",
  "upi.callback.received",
  "ledger.entry.created",
]);

export default { TXN_STATUS, HEALTH_STATUS, ERROR_CATEGORIES, KAFKA_TOPICS };

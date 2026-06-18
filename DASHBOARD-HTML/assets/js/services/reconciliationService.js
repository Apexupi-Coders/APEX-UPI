import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

export function getReconciliation(options) {
  return api.get(ENDPOINTS.reconciliation, options);
}

export default { getReconciliation };

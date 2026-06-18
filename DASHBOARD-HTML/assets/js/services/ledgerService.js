import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

export function getLedger(options) {
  return api.get(ENDPOINTS.ledger, options);
}

export default { getLedger };

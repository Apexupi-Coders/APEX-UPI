import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

export function getAuditLog(options) {
  return api.get(ENDPOINTS.audit, options);
}

export default { getAuditLog };

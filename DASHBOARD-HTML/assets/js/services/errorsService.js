import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

export function getErrors(options) {
  return api.get(ENDPOINTS.errors, options);
}

export default { getErrors };

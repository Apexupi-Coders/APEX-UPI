import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

export function getEvents(options) {
  return api.get(ENDPOINTS.events, options);
}

export default { getEvents };

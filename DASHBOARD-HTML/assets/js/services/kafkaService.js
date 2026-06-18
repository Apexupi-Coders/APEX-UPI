import api from "../core/api.js";
import { ENDPOINTS } from "../core/config.js";

export function getKafkaStatus(options) {
  return api.get(ENDPOINTS.kafkaStatus, options);
}

export default { getKafkaStatus };

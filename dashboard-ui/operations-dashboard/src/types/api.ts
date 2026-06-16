export type ServiceStatus = {
  status: string;
};

export type OpsHealthResponse = {
  timestamp?: string;
  services: Record<string, ServiceStatus>;
};

export type TransactionJourneyDto = {
  txnId?: string;
  correlationId?: string | null;
  approvalRefNo?: string | null;
  amount?: number | string | null;
  payer?: string | null;
  payee?: string | null;
  stateChanges?: Array<{
    fromState?: string | null;
    toState?: string | null;
    eventType?: string | null;
    occurredAt?: string | null;
    details?: string | null;
  }>;
};

export type OpsSearchResponse = {
  timestamp?: string;
  results?: Array<Record<string, unknown>>;
};


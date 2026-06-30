export type ServiceStatus = {
  status: string;
};

export type ServiceHealthStatus = {
  serviceName: string;
  status: string;
  latencyMs: number;
  timestamp?: string;
  httpStatus?: number | null;
  errorMessage?: string | null;
};

export type ControlStatusResponse = {
  toggles: {
    npciFailureMode: boolean;
    cbsFailureMode: boolean;
    npciWebhookSuppressed: boolean;
  };
  transactionCounts: {
    PENDING: number;
    SUBMITTED: number;
    SUCCESS: number;
    FAILED: number;
    UNKNOWN: number;
    COMPENSATED: number;
  };
  serviceSizes: {
    totalTransactions: number;
    idempotencyKeys: number;
    ledgerEntries: number;
  };
  infrastructure: {
    database: string;
    cache: string;
    messaging: string;
    cryptoEnabled: boolean;
  };
};

export type OverviewResponse = {
  timestamp?: string;
  overallStatus?: string;
  services: ServiceHealthStatus[];
  healthyCount: number;
  unhealthyCount: number;
  degradedCount: number;
  totalCount: number;
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


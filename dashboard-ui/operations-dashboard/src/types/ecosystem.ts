export type EcosystemStatus = 'UP' | 'DEGRADED' | 'DOWN';

export interface ServiceHealth {
  serviceName: string;
  status: EcosystemStatus;
  tps?: number;
  successRate?: number; // 0..1
  failureRate?: number; // 0..1
  latencyP95Ms?: number;
  errorCounts?: Record<string, number>;
  queueDepth?: number;
  lastUpdatedAt?: string; // ISO
}

export interface VMHealth {
  vmId: string;
  hostname?: string;
  status: EcosystemStatus;
  services: ServiceHealth[];
  metrics?: {
    cpuUtilPct?: number;
    memUtilPct?: number;
    diskUtilPct?: number;
    netInRateBps?: number;
    netOutRateBps?: number;
    lastUpdatedAt?: string;
  };
}

export interface EcosystemHealth {
  status: EcosystemStatus;
  tps: number;
  successRate: number; // 0..1
  failureRate: number; // 0..1
  latencyP95Ms: number;
  queueDepthTotal?: number;
  lastUpdatedAt: string; // ISO
}

export interface TransactionTraceHop {
  serviceName: string;
  status: EcosystemStatus | 'PENDING' | 'SKIPPED';
  processingTimeMs?: number;
  startedAt?: string;
  finishedAt?: string;
  errors?: {
    code?: string;
    message?: string;
    category?: string;
  }[];
  correlationIds?: Record<string, string>;
}

export interface TransactionTrace {
  txnId: string;
  customerId?: string;
  traceId?: string;
  status: EcosystemStatus | 'FAILED' | 'SUCCESS';
  overallProcessingTimeMs?: number;
  failedHop?: string;
  hops: TransactionTraceHop[];
  lastUpdatedAt?: string;
}

export interface Alert {
  alertId: string;
  severity: 'CRITICAL' | 'MAJOR' | 'MINOR';
  serviceName?: string;
  vmId?: string;
  status: 'ACTIVE' | 'RESOLVED';
  title: string;
  description?: string;
  createdAt: string;
  updatedAt?: string;
  correlatedRootCauseId?: string;
}

export interface DependencyNode {
  id: string;
  label: string;
  serviceNames?: string[];
  status: EcosystemStatus;
}

export interface DependencyEdge {
  from: string;
  to: string;
  label?: string;
  status: EcosystemStatus;
}

export interface DependencyGraph {
  nodes: DependencyNode[];
  edges: DependencyEdge[];
  lastUpdatedAt: string;
}

export interface RootCauseAnalysis {
  rootCauseId: string;
  summary: string;
  detectedAt: string;
  contributingFactors: {
    serviceName?: string;
    vmId?: string;
    signal: string;
    severity: 'INFO' | 'WARN' | 'ERROR';
    evidence?: Record<string, unknown>;
  }[];
  remediationSuggestions?: string[];
  correlatedAlerts?: string[];
  correlatedTxnIds?: string[];
}


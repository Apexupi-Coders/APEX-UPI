import React from 'react';
import { usePolling } from '../hooks/usePolling';
import { fetchEcosystemRootCause } from '../services/opsApi';
import RootCauseCard from '../components/rootcause/RootCauseCard';
import ImpactedServicesTable from '../components/rootcause/ImpactedServicesTable';
import FailureTimeline from '../components/rootcause/FailureTimeline';
import SuggestedResolutionCard from '../components/rootcause/SuggestedResolutionCard';
import DependencyChainVisualization from '../components/rootcause/DependencyChainVisualization';

type RootCauseResponse = {

  rootCauseId?: string;
  summary?: string;
  confidence?: number;
  lastUpdatedAt?: string;
  contributingFactors?: Array<{
    serviceName?: string;
    vmId?: string;
    signal?: string;
    severity?: string;
    evidence?: Record<string, unknown>;
  }>;
  impactedServices?: Array<{
    serviceName?: string;
    status?: string;
    failureRate?: number;
    latencyP95Ms?: number;
    queueDepth?: number;
  }>;
  failureTimeline?: Array<{
    occurredAt?: string;
    stage?: string;
    severity?: string;
    message?: string;
  }>;
  remediationSuggestions?: string[];
  suggestedAction?: string;
  /** Optional dependency chain returned by backend */
  dependencyChain?: unknown;
};


export default function RootCausePage() {
  // Optionally support drill-down params in the future.
  // For now, fetch cross-VM root-cause without query params.
  const { data, error, loading } = usePolling(() => fetchEcosystemRootCause(undefined), { intervalMs: 4000, enabled: true });

  const rc = (data as RootCauseResponse | undefined) ?? undefined;

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-sm font-semibold text-slate-200">Root Cause Analysis</div>
            <div className="mt-1 text-xs text-slate-400">Cross-VM correlation across PSP, NPCI, Kafka, Redis, Ledger and reconciliation.</div>
          </div>
          <div className="text-xs text-slate-500">{loading ? 'Refreshing…' : rc?.lastUpdatedAt ?? ''}</div>
        </div>

        {error ? <div className="mt-3 text-xs text-rose-200">{error.message}</div> : null}
      </div>

      <RootCauseCard rootCause={rc} loading={loading} />
      <DependencyChainVisualization dependencyGraph={rc?.dependencyChain as any} loading={loading} />
      <ImpactedServicesTable

        impactedServices={rc?.impactedServices ?? []}
        loading={loading}
        onNavigateToDashboard={({ dashboardTarget }) => {
          if (dashboardTarget.kind === 'none') return;
          // Navigation for impacted service dashboards is intentionally best-effort.
          // The underlying hash router only supports known pages.
          // If route target is unknown, do nothing.
          const mapping = {
            'psp-live': 'live',
            'npci-health': 'health',
            kafka: 'kafka',
            ledger: 'ledger',
            audit: 'audit',
          } as const;
          const route = mapping[dashboardTarget.dashboard];
          if (route) {
            window.location.hash = (window as unknown as { location: Location }).location.hash; // no-op; keep TS happy
            // Actually navigate using existing router mechanism where possible.
            window.location.hash = `#/${route}`;
          }
        }}
      />
      <FailureTimeline timeline={rc?.failureTimeline ?? []} loading={loading} />
      <SuggestedResolutionCard
        remediationSuggestions={rc?.remediationSuggestions ?? []}
        suggestedAction={rc?.suggestedAction}
        loading={loading}
      />
    </div>
  );
}


import React from 'react';

type ImpactedService = {
  serviceName?: string;
  status?: string;
  failureRate?: number;
  latencyP95Ms?: number;
  queueDepth?: number;
};

function formatPct(v?: number) {
  if (typeof v !== 'number') return undefined;
  return `${Math.round(v * 100)}%`;
}

type NavigationTarget =
  | { kind: 'dashboard'; dashboard: 'psp-live' | 'npci-health' | 'kafka' | 'ledger' | 'audit' }
  | { kind: 'none' };

function getDashboardTargetForService(serviceName?: string): NavigationTarget {
  const s = (serviceName ?? '').toLowerCase();

  // Note: keep this mapping conservative to avoid breaking existing behavior.
  if (!s) return { kind: 'none' };
  if (s.includes('psp') || s.includes('psp-ledger')) return { kind: 'dashboard', dashboard: 'psp-live' };
  if (s.includes('npci')) return { kind: 'dashboard', dashboard: 'npci-health' };
  if (s.includes('kafka')) return { kind: 'dashboard', dashboard: 'kafka' };
  if (s.includes('ledger')) return { kind: 'dashboard', dashboard: 'ledger' };
  if (s.includes('audit')) return { kind: 'dashboard', dashboard: 'audit' };

  return { kind: 'none' };
}

export default function ImpactedServicesTable({
  impactedServices,
  loading,
  onNavigateToDashboard,
}: {
  impactedServices: ImpactedService[];
  loading?: boolean;
  onNavigateToDashboard?: (args: { serviceName?: string; dashboardTarget: NavigationTarget }) => void;
}) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="text-sm font-semibold text-slate-200">Impacted Services</div>
      <div className="mt-2 overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-800 text-slate-400">
              <th className="py-2 pr-3">Service</th>
              <th className="py-2 pr-3">Status</th>
              <th className="py-2 pr-3">Failure Rate</th>
              <th className="py-2 pr-3">Latency P95</th>
              <th className="py-2">Queue Depth</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td className="py-6 text-slate-500" colSpan={5}>
                  Loading…
                </td>
              </tr>
            ) : impactedServices.length === 0 ? (
              <tr>
                <td className="py-6 text-slate-500" colSpan={5}>
                  No impacted services returned.
                </td>
              </tr>
            ) : (
              impactedServices.map((s, idx) => (
                <tr key={idx} className="border-b border-slate-800/40">
                  {(() => {
                    const dashboardTarget = getDashboardTargetForService(s.serviceName);
                    const isClickable = dashboardTarget.kind !== 'none';

                    if (!isClickable) {
                      return (
                        <>
                          <td className="py-2 pr-3 text-slate-200">{s.serviceName ?? '—'}</td>
                          <td className="py-2 pr-3 text-slate-300">{s.status ?? '—'}</td>
                          <td className="py-2 pr-3 text-slate-300">{formatPct(s.failureRate) ?? '—'}</td>
                          <td className="py-2 pr-3 text-slate-300">{typeof s.latencyP95Ms === 'number' ? `${s.latencyP95Ms}ms` : '—'}</td>
                          <td className="py-2 text-slate-300">{typeof s.queueDepth === 'number' ? s.queueDepth : '—'}</td>
                        </>
                      );
                    }

                    return (
                      <>
                        <td
                          className="py-2 pr-3 text-slate-200 underline underline-offset-2 decoration-slate-400 cursor-pointer"
                          onClick={() =>
                            onNavigateToDashboard?.({
                              serviceName: s.serviceName,
                              dashboardTarget,
                            })
                          }
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault();
                              onNavigateToDashboard?.({
                                serviceName: s.serviceName,
                                dashboardTarget,
                              });
                            }
                          }}
                          aria-label={`Open ${s.serviceName} dashboard`}
                        >
                          {s.serviceName ?? '—'}
                        </td>
                        <td className="py-2 pr-3 text-slate-300">{s.status ?? '—'}</td>
                        <td className="py-2 pr-3 text-slate-300">{formatPct(s.failureRate) ?? '—'}</td>
                        <td className="py-2 pr-3 text-slate-300">{typeof s.latencyP95Ms === 'number' ? `${s.latencyP95Ms}ms` : '—'}</td>
                        <td className="py-2 text-slate-300">{typeof s.queueDepth === 'number' ? s.queueDepth : '—'}</td>
                      </>
                    );
                  })()}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}


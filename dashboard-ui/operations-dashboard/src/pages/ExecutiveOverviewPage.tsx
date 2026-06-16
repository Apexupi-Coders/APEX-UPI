import React from 'react';
import { usePolling } from '../hooks/usePolling';
import { fetchHealth } from '../services/opsApi';

function StatusPill({ status }: { status: string }) {
  const color =
    status.toUpperCase() === 'UP' || status.toUpperCase() === 'OK'
      ? 'bg-emerald-500/20 text-emerald-200 ring-1 ring-emerald-400/30'
      : status.toUpperCase() === 'DOWN' || status.toUpperCase() === 'ERROR'
        ? 'bg-rose-500/20 text-rose-200 ring-1 ring-rose-400/30'
        : 'bg-amber-500/20 text-amber-200 ring-1 ring-amber-400/30';

  return <span className={`inline-flex rounded-full px-2 py-1 text-xs ${color}`}>{status}</span>;
}

export default function ExecutiveOverviewPage() {
  const { data, error, loading } = usePolling(fetchHealth, { intervalMs: 4000, enabled: true });

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-sm font-semibold text-slate-200">Executive Overview</div>
            <div className="mt-1 text-xs text-slate-400">
              Control room summary driven by the read-only operations API.
            </div>
          </div>
          <div className="text-xs text-slate-500">{loading ? 'Refreshing…' : data?.timestamp ?? ''}</div>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {data?.services
            ? Object.entries(data.services).map(([name, s]) => (
                <div key={name} className="rounded-lg border border-slate-800 bg-slate-950/30 p-3">
                  <div className="text-xs text-slate-400">{name}</div>
                  <div className="mt-2">
                    <StatusPill status={s.status} />
                  </div>
                </div>
              ))
            : null}
        </div>

        {error ? <div className="mt-3 text-xs text-rose-200">{error.message}</div> : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Transaction Throughput</div>
          <div className="mt-2 text-xs text-slate-400">Wire later: counts + rates per state.</div>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Risk & Compliance</div>
          <div className="mt-2 text-xs text-slate-400">Wire later: security + validation failures.</div>
        </div>
      </div>
    </div>
  );
}


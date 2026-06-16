import React from 'react';
import { usePolling } from '../hooks/usePolling';
import { fetchHealth } from '../services/opsApi';

export default function ServiceHealthPage() {
  const { data, error, loading } = usePolling(fetchHealth, { intervalMs: 3000 });
  const services = data?.services ?? {};

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Service Health Center</div>
        <div className="mt-1 text-xs text-slate-400">Uptime/status indicators for core services.</div>

        {error ? <div className="mt-3 text-xs text-rose-200">{error.message}</div> : null}

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          {Object.entries(services).map(([name, s]) => {
            const st = s.status.toUpperCase();
            const ring = st === 'UP' || st === 'OK'
              ? 'ring-emerald-400/40 bg-emerald-500/10'
              : st === 'DOWN' || st === 'ERROR'
                ? 'ring-rose-400/40 bg-rose-500/10'
                : 'ring-amber-400/40 bg-amber-500/10';

            return (
              <div key={name} className={`relative rounded-xl border border-slate-800 ${ring} p-4`}>
                <div className="text-xs text-slate-300">{name}</div>
                <div className="mt-2 flex items-center justify-between gap-3">
                  <div className="text-xl font-semibold text-slate-100">{loading ? '…' : s.status}</div>
                  <div className="h-3 w-3 rounded-full bg-slate-500" />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}


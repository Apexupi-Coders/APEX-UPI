import React from 'react';
import { ErrorBoundary } from '../components/error/ErrorBoundary';

const nodes = [
  { key: 'TPAP', status: 'OK' },
  { key: 'API Gateway', status: 'OK' },
  { key: 'Kafka', status: 'DEGRADED' },
  { key: 'Transaction Orchestrator', status: 'OK' },
  { key: 'Transaction State Service', status: 'OK' },
  { key: 'Redis Idempotency', status: 'OK' },
  { key: 'NPCI Adapter', status: 'OK' },
  { key: 'CBS Adapter', status: 'OK' },
  { key: 'CBS', status: 'OK' },
  { key: 'Reconciliation Service', status: 'OK' },
  { key: 'Audit Service', status: 'OK' },
];

function statusColor(status: string) {
  const s = status.toUpperCase();
  if (s === 'OK' || s === 'UP') return 'border-emerald-400/40 bg-emerald-500/10 text-emerald-200';
  if (s === 'DOWN' || s === 'ERROR') return 'border-rose-400/40 bg-rose-500/10 text-rose-200';
  return 'border-amber-400/40 bg-amber-500/10 text-amber-200';
}

export default function ArchitecturePage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div>
            <div className="text-sm font-semibold text-slate-200">Architecture Health View</div>
            <div className="mt-1 text-xs text-slate-400">
              Read-only component health tiles aligned to the APEX-UPI architecture diagram (mock-first).
            </div>
          </div>
          <div className="text-xs text-slate-500">Refresh: mock</div>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {nodes.map((n) => (
            <div
              key={n.key}
              className={`rounded-xl border bg-slate-950/20 p-4 ${statusColor(n.status)}`}
            >
              <div className="text-[11px] text-slate-200/80">{n.key}</div>
              <div className="mt-2 text-lg font-semibold">{n.status}</div>
              <div className="mt-3 h-2 w-full rounded bg-slate-900/60 overflow-hidden">
                <div
                  className="h-full rounded bg-brand-500/70"
                  style={{ width: n.status === 'OK' ? '92%' : n.status === 'DEGRADED' ? '62%' : '20%' }}
                />
              </div>
            </div>
          ))}
        </div>
      </div>

      <ErrorBoundary fallback={<div className="text-xs text-rose-200">Failed to render diagram.</div>}>
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Topology Overview</div>
          <div className="mt-1 text-xs text-slate-400">
            Placeholder real-time diagram; designed to plug into live health + audit events later.
          </div>

          <div className="mt-6 overflow-x-auto">
            <div className="min-w-[900px] flex flex-wrap items-center gap-3">
              {nodes.map((n) => (
                <div key={n.key} className={`rounded-xl border px-4 py-2 text-xs font-semibold ${statusColor(n.status)}`}>
                  {n.key}
                </div>
              ))}
            </div>
          </div>
        </div>
      </ErrorBoundary>
    </div>
  );
}


import React from 'react';

export default function ReconciliationPage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Reconciliation Dashboard</div>
        <div className="mt-1 text-xs text-slate-400">
          Read-only reconciliation status. Wire reconciliation endpoints when available.
        </div>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
            <div className="text-xs font-semibold text-slate-300">Mismatches</div>
            <div className="mt-2 text-2xl font-semibold text-rose-200">—</div>
            <div className="mt-1 text-xs text-slate-500">No live reconciliation metrics yet.</div>
          </div>
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
            <div className="text-xs font-semibold text-slate-300">Matched</div>
            <div className="mt-2 text-2xl font-semibold text-emerald-200">—</div>
            <div className="mt-1 text-xs text-slate-500">No live reconciliation metrics yet.</div>
          </div>
        </div>
      </div>
    </div>
  );
}


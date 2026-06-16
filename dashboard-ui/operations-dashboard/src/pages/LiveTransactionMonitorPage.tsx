import React, { useMemo, useState } from 'react';
import { ErrorBoundary } from '../components/error/ErrorBoundary';
import { usePolling } from '../hooks/usePolling';
import { fetchJourneyByTid } from '../services/opsApi';

type TxnRow = {
  tid: string;
  state: string;
  payer: string;
  payee: string;
};

export default function LiveTransactionMonitorPage() {
  const [tid, setTid] = useState('PSP-DEMO-0001');

  // This uses journey-by-tid as the only available backend endpoint (read-only).
  const { data, error, loading } = usePolling(() => fetchJourneyByTid(tid), {
    intervalMs: 2500,
    enabled: Boolean(tid),
  });

  const rows: TxnRow[] = useMemo(() => {
    const state = data?.stateChanges?.[data.stateChanges.length - 1]?.toState ?? '—';
    return [
      {
        tid: data?.txnId ?? tid,
        state,
        payer: data?.payer ?? '—',
        payee: data?.payee ?? '—',
      },
    ];
  }, [data, tid]);

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="text-sm font-semibold text-slate-200">Live Transaction Monitor</div>
            <div className="mt-1 text-xs text-slate-400">Read-only journey lookup for rapid operator triage.</div>
          </div>
          <div className="flex items-center gap-2">
            <input
              className="h-9 w-full rounded-lg border border-slate-800 bg-slate-950/40 px-3 text-sm outline-none focus:ring-2 focus:ring-brand-500/40 md:w-72"
              value={tid}
              onChange={(e) => setTid(e.target.value)}
              placeholder="Transaction ID (tid)"
            />
          </div>
        </div>

        {error ? <div className="mt-3 text-xs text-rose-200">{error.message}</div> : null}

        <div className="mt-4 overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-xs text-slate-400">
              <tr>
                <th className="px-3 py-2 font-medium">Transaction ID</th>
                <th className="px-3 py-2 font-medium">State</th>
                <th className="px-3 py-2 font-medium">Payer</th>
                <th className="px-3 py-2 font-medium">Payee</th>
                <th className="px-3 py-2 font-medium">Latency</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {rows.map((r) => (
                <tr key={r.tid} className="hover:bg-slate-900/30">
                  <td className="px-3 py-3 font-mono text-xs text-slate-200">{r.tid}</td>
                  <td className="px-3 py-3">
                    <span className="rounded-full bg-slate-800/60 px-2 py-1 text-xs text-slate-200">
                      {loading ? '…' : r.state}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-xs text-slate-300">{r.payer}</td>
                  <td className="px-3 py-3 text-xs text-slate-300">{r.payee}</td>
                  <td className="px-3 py-3 text-xs text-slate-500">N/A</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <ErrorBoundary>
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Next Action Suggestions</div>
          <div className="mt-2 text-xs text-slate-400">
            When real event-stream endpoints are wired, this section will show automated triage.
          </div>
        </div>
      </ErrorBoundary>
    </div>
  );
}


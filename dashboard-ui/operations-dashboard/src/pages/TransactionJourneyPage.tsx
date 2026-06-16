import React, { useMemo, useState } from 'react';
import { fetchJourneyByTid } from '../services/opsApi';
import { usePolling } from '../hooks/usePolling';

export default function TransactionJourneyPage() {
  const [tid, setTid] = useState('PSP-DEMO-0001');
  const { data, loading, error } = usePolling(() => fetchJourneyByTid(tid), { intervalMs: 2500, enabled: Boolean(tid) });

  const journey = useMemo(() => data?.stateChanges ?? [], [data]);
  const last = journey[journey.length - 1];

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="text-sm font-semibold text-slate-200">Transaction Journey Timeline</div>
            <div className="mt-1 text-xs text-slate-400">Operator view of states, references and timestamps.</div>
          </div>
          <input
            className="h-9 w-full rounded-lg border border-slate-800 bg-slate-950/40 px-3 text-sm outline-none focus:ring-2 focus:ring-brand-500/40 md:w-72"
            value={tid}
            onChange={(e) => setTid(e.target.value)}
            placeholder="tid"
          />
        </div>

        {error ? <div className="mt-3 text-xs text-rose-200">{error.message}</div> : null}

        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3">
            <div className="text-xs text-slate-400">Transaction ID</div>
            <div className="mt-1 font-mono text-xs text-slate-200">{data?.txnId ?? '—'}</div>
          </div>
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3">
            <div className="text-xs text-slate-400">Approval Reference Number</div>
            <div className="mt-1 font-mono text-xs text-slate-200">{data?.approvalRefNo ?? '—'}</div>
          </div>
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3 md:col-span-2">
            <div className="text-xs text-slate-400">Current State</div>
            <div className="mt-1 text-sm text-brand-200">{loading ? '…' : last?.toState ?? '—'}</div>
          </div>
        </div>

        <div className="mt-4">
          <div className="text-xs font-semibold text-slate-300">State Changes</div>
          <div className="mt-2 rounded-lg border border-slate-800 bg-slate-950/20 p-3">
            {journey.length === 0 ? (
              <div className="text-xs text-slate-500">No timeline available for this tid yet.</div>
            ) : (
              <ol className="space-y-3">
                {journey.map((c, idx) => (
                  <li key={idx} className="rounded-md border border-slate-800 bg-slate-950/30 p-3">
                    <div className="flex items-center justify-between gap-3">
                      <div className="text-xs font-semibold text-slate-200">
                        {c.fromState ?? '—'} → {c.toState ?? '—'}
                      </div>
                      <div className="text-[11px] text-slate-500">{c.occurredAt ?? ''}</div>
                    </div>
                    <div className="mt-1 text-xs text-slate-300">{c.eventType ?? 'STATE_CHANGE'}</div>
                    {c.details ? <div className="mt-1 text-xs text-slate-400">{c.details}</div> : null}
                  </li>
                ))}
              </ol>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}


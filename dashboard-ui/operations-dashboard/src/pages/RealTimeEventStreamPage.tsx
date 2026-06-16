import React from 'react';
import { ErrorBoundary } from '../components/error/ErrorBoundary';
import { usePolling } from '../hooks/usePolling';
import { fetchJourneyByTid } from '../services/opsApi';

export default function RealTimeEventStreamPage() {
  const demoTid = 'PSP-DEMO-0001';
  const { data } = usePolling(() => fetchJourneyByTid(demoTid), { intervalMs: 3000 });

  const events = (data?.stateChanges ?? []).slice().reverse().map((c, idx) => ({
    id: `${idx}-${c.occurredAt ?? ''}`,
    type: c.eventType ?? (c.toState ? `STATE_${c.toState}` : 'EVENT'),
    ts: c.occurredAt ?? '',
    details: c.details ?? c.toState ?? '',
  }));

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Real-Time Event Stream</div>
        <div className="mt-1 text-xs text-slate-400">
          Converts backend journey state changes into operator-friendly business events.
        </div>

        <ErrorBoundary fallback={<div className="mt-3 text-xs text-rose-200">Event stream unavailable.</div>}>
          <div className="mt-4 max-h-[520px] overflow-auto rounded-lg border border-slate-800 bg-slate-950/20">
            <ul className="divide-y divide-slate-800 text-sm">
              {events.length === 0 ? (
                <li className="p-4 text-xs text-slate-500">No events yet.</li>
              ) : (
                events.map((e) => (
                  <li key={e.id} className="px-4 py-3">
                    <div className="flex items-center justify-between gap-3">
                      <div className="text-xs font-semibold text-brand-200">{e.type}</div>
                      <div className="text-[11px] text-slate-500">{e.ts}</div>
                    </div>
                    <div className="mt-1 text-xs text-slate-300">{e.details}</div>
                  </li>
                ))
              )}
            </ul>
          </div>
        </ErrorBoundary>
      </div>
    </div>
  );
}


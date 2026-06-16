import React, { useEffect, useState } from 'react';
import { ErrorBoundary } from '../components/error/ErrorBoundary';

type DemoEvent = { type: string; ts: string; details?: string };

const demoSequence: DemoEvent[] = [
  { type: 'PAYMENT_RECEIVED', ts: 't+00.0s', details: 'TPAP POST accepted' },
  { type: 'VALIDATION_PASSED', ts: 't+00.1s', details: 'Rules engine approved the request' },
  { type: 'PENDING', ts: 't+00.2s', details: 'State persisted as PENDING' },
  { type: 'SUBMITTED', ts: 't+00.3s', details: 'NPCI request sent (Kafka outbound)' },
  { type: 'NPCI_SUCCESS', ts: 't+01.2s', details: 'NPCI inbound response indicates success' },
  { type: 'LEDGER_RECORDED', ts: 't+01.3s', details: 'Ledger write acknowledged' },
  { type: 'SUCCESS', ts: 't+01.4s', details: 'Webhook delivered + final success' },
];

export default function DemoModePage() {
  const [active, setActive] = useState(0);

  useEffect(() => {
    const t = window.setInterval(() => {
      setActive((n) => (n + 1 < demoSequence.length ? n + 1 : n));
    }, 900);
    return () => window.clearInterval(t);
  }, []);

  const events = demoSequence.slice(0, active + 1);

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Mentor Demo Mode</div>
        <div className="mt-1 text-xs text-slate-400">Realistic transaction playback for operator training.</div>

        <ErrorBoundary fallback={<div className="text-xs text-rose-200">Demo playback failed.</div>}>
          <div className="mt-4 grid gap-4 lg:grid-cols-2">
            <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
              <div className="text-xs font-semibold text-slate-300">Playback Timeline</div>
              <ol className="mt-3 space-y-2">
                {events.map((e, idx) => (
                  <li key={idx} className="rounded-md border border-slate-800 bg-slate-950/30 p-3">
                    <div className="flex items-center justify-between gap-3">
                      <div className="text-xs font-semibold text-brand-200">{e.type}</div>
                      <div className="text-[11px] text-slate-500">{e.ts}</div>
                    </div>
                    {e.details ? <div className="mt-1 text-xs text-slate-300">{e.details}</div> : null}
                  </li>
                ))}
              </ol>
            </div>
            <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
              <div className="text-xs font-semibold text-slate-300">Operator Notes</div>
              <div className="mt-2 text-xs text-slate-400">
                Use this view to demonstrate the end-to-end path: TPAP → Kafka → Orchestrator → Kafka → NPCI Adapter → NPCI → Webhook → Ledger.
              </div>
              <div className="mt-4 text-xs text-slate-500">When connected to live ops endpoints, this module can drive the live UI components.</div>
            </div>
          </div>
        </ErrorBoundary>
      </div>
    </div>
  );
}


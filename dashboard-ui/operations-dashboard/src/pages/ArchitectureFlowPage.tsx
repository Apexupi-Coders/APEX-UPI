import React from 'react';

const steps = [
  'TPAP',
  'Kafka',
  'Transaction Orchestrator',
  'Kafka',
  'NPCI Adapter',
  'NPCI',
  'Webhook',
  'Ledger',
  'SUCCESS',
];

export default function ArchitectureFlowPage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Architecture Flow Visualization</div>
        <div className="mt-1 text-xs text-slate-400">Animated operator-friendly flow from ingress to ledger success.</div>

        <div className="mt-6 overflow-x-auto">
          <div className="flex items-start gap-4 min-w-[900px]">
            {steps.map((s, idx) => (
              <div key={s} className="flex flex-col items-center">
                <div
                  className={`relative rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3 text-center text-xs font-semibold text-slate-200 ${
                    idx === steps.length - 1 ? 'border-emerald-400/40 bg-emerald-500/10' : ''
                  }`}
                >
                  <span className="whitespace-nowrap">{s}</span>
                  <span className="pointer-events-none absolute -right-3 top-1/2 h-2 w-2 -translate-y-1/2 rounded-full bg-brand-500/80" />
                </div>
                {idx !== steps.length - 1 ? (
                  <div className="mt-4 text-xs text-slate-500">↓</div>
                ) : null}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}


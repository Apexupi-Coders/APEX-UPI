import React from 'react';

type Stat = {
  label: string;
  value: string;
  hint?: string;
};

const overviewStats: Stat[] = [
  { label: 'TPS', value: 'N/A', hint: 'Wire from overview metrics endpoint' },
  { label: 'Success Rate', value: 'N/A' },
  { label: 'Failure Rate', value: 'N/A' },
  { label: 'Pending Transactions', value: 'N/A' },
  { label: 'p95 Latency', value: 'N/A' },
  { label: 'Event Freshness', value: 'mock' },
];

export default function OverviewPage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div>
            <div className="text-sm font-semibold text-slate-200">Operations Command Center</div>
            <div className="mt-1 text-xs text-slate-400">
              Enterprise read-only control room (mock-first). Ready to wire to ops APIs.
            </div>
          </div>
          <div className="text-xs text-slate-500">Last refresh: mock</div>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {overviewStats.map((s) => (
            <div key={s.label} className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
              <div className="text-xs text-slate-400">{s.label}</div>
              <div className="mt-2 text-xl font-semibold text-slate-100">{s.value}</div>
              {s.hint ? <div className="mt-1 text-[11px] text-slate-500">{s.hint}</div> : null}
            </div>
          ))}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Hot Paths</div>
          <div className="mt-2 text-xs text-slate-400">Read-only path health + triage signals (mock).</div>
          <div className="mt-4 space-y-2">
            {['TPAP → Kafka', 'Orchestrator → NPCI', 'CBS → SUCCESS'].map((t) => (
              <div key={t} className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/20 px-3 py-2">
                <div className="text-xs text-slate-300">{t}</div>
                <div className="text-xs font-semibold text-amber-200">MONITOR</div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Triage Inbox</div>
          <div className="mt-2 text-xs text-slate-400">Most recent operator actions needed (mock).</div>
          <div className="mt-4 overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="text-xs text-slate-400">
                <tr>
                  <th className="px-3 py-2 font-medium">Category</th>
                  <th className="px-3 py-2 font-medium">Severity</th>
                  <th className="px-3 py-2 font-medium">Reference</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {[
                  { c: 'Idempotency', sev: 'WARN', r: 'redis:key:*' },
                  { c: 'NPCI Latency', sev: 'INFO', r: 'timeout:p99' },
                  { c: 'CBS Failures', sev: 'ERROR', r: 'txnBatch:demo' },
                ].map((row) => (
                  <tr key={row.c} className="hover:bg-slate-900/30">
                    <td className="px-3 py-3 text-xs text-slate-300">{row.c}</td>
                    <td className="px-3 py-3 text-xs">
                      <span className="rounded-full bg-slate-800/60 px-2 py-1 text-xs text-slate-200">
                        {row.sev}
                      </span>
                    </td>
                    <td className="px-3 py-3 font-mono text-[11px] text-slate-500">{row.r}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}


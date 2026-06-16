import React from 'react';

export default function ErrorIntelligencePage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Error Intelligence Center</div>
        <div className="mt-1 text-xs text-slate-400">
          Classify errors into validation, kafka, callback, database and security categories.
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-2 lg:grid-cols-5">
          {[
            ['Validation Errors', 'amber'],
            ['Kafka Errors', 'rose'],
            ['Callback Errors', 'fuchsia'],
            ['Database Errors', 'sky'],
            ['Security Errors', 'violet'],
          ].map(([label]) => (
            <div key={label} className="rounded-lg border border-slate-800 bg-slate-950/20 p-3">
              <div className="text-xs text-slate-400">{label}</div>
              <div className="mt-2 text-sm text-slate-200">—</div>
            </div>
          ))}
        </div>

        <div className="mt-4 text-xs text-slate-500">Wire /api/v1/ops/* error endpoints when available.</div>
      </div>
    </div>
  );
}


import React from 'react';

type RootCause = {
  rootCauseId?: string;
  summary?: string;
  confidence?: number;
  lastUpdatedAt?: string;
};

export default function RootCauseCard({
  rootCause,
  loading,
}: {
  rootCause?: RootCause;
  loading?: boolean;
}) {
  const confidencePct =
    typeof rootCause?.confidence === 'number' ? Math.round(rootCause.confidence * 100) : undefined;

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="text-sm font-semibold text-slate-200">Probable Root Cause</div>
      <div className="mt-2 flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="text-xs text-slate-400">Root Cause ID</div>
          <div className="mt-1 font-mono text-xs text-slate-200">{loading ? '…' : rootCause?.rootCauseId ?? '—'}</div>
        </div>

        <div className="text-right">
          <div className="text-xs text-slate-400">Confidence</div>
          <div className="mt-1 text-sm font-semibold text-brand-200">
            {loading ? '…' : confidencePct === undefined ? '—' : `${confidencePct}%`}
          </div>
        </div>
      </div>

      <div className="mt-4">
        <div className="text-xs text-slate-400">Summary</div>
        <div className="mt-1 text-sm text-slate-100">
          {loading ? 'Correlating signals across PSP, NPCI, Kafka, Redis, Ledger…' : rootCause?.summary ?? '—'}
        </div>
      </div>

      {rootCause?.lastUpdatedAt ? (
        <div className="mt-4 text-[11px] text-slate-500">Updated: {rootCause.lastUpdatedAt}</div>
      ) : null}
    </div>
  );
}


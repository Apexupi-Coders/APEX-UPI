import React from 'react';

type TimelineItem = {
  occurredAt?: string;
  stage?: string;
  severity?: string;
  message?: string;
};

export default function FailureTimeline({
  timeline,
  loading,
}: {
  timeline: TimelineItem[];
  loading?: boolean;
}) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="text-sm font-semibold text-slate-200">Failure Timeline</div>
      <div className="mt-2 text-xs text-slate-400">Chronological view of correlated failures.</div>

      <div className="mt-4 rounded-lg border border-slate-800 bg-slate-950/20 p-3">
        {loading ? (
          <div className="text-xs text-slate-500">Loading timeline…</div>
        ) : timeline.length === 0 ? (
          <div className="text-xs text-slate-500">No timeline available.</div>
        ) : (
          <ol className="space-y-3">
            {timeline.map((t, idx) => (
              <li key={idx} className="rounded-md border border-slate-800/50 bg-slate-950/30 p-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="text-xs font-semibold text-slate-200">{t.stage ?? '—'}</div>
                  <div className="text-[11px] text-slate-500">{t.occurredAt ?? ''}</div>
                </div>
                <div className="mt-1 text-xs text-slate-300">{t.message ?? ''}</div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}


import React from 'react';

export default function AuditPage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Audit Dashboard</div>
        <div className="mt-1 text-xs text-slate-400">Read-only audit trail view. Wire audit endpoints when available.</div>
        <div className="mt-4 text-xs text-slate-500">No audit data available from current API scaffold.</div>
      </div>
    </div>
  );
}


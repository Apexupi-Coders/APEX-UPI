import React from 'react';

export function TopBar() {
  return (
    <div className="border-b border-slate-800 bg-slate-950/60">
      <div className="mx-auto flex max-w-[1400px] items-center justify-between gap-4 p-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-500/15 ring-1 ring-brand-500/30">
            <div className="h-4 w-4 rounded bg-brand-500" />
          </div>
          <div>
            <div className="text-sm font-semibold text-slate-100">APEX-UPI Operations</div>
            <div className="text-[11px] text-slate-500">Read-only fintech control room</div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="rounded-lg border border-slate-800 bg-slate-900/40 px-3 py-2 text-xs text-slate-200">
            Dark Mode
          </div>
        </div>
      </div>
    </div>
  );
}


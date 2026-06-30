import React from 'react';

type SuggestedResolution = {
  remediationSuggestions?: string[];
  suggestedAction?: string;
};

export default function SuggestedResolutionCard({
  remediationSuggestions,
  suggestedAction,
  loading,
}: {
  remediationSuggestions: string[];
  suggestedAction?: string;
  loading?: boolean;
}) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="text-sm font-semibold text-slate-200">Recommended Action</div>
      <div className="mt-2 text-xs text-slate-400">Actionable next steps based on correlated failures.</div>

      {loading ? <div className="mt-4 text-xs text-slate-500">Loading suggestions…</div> : null}

      {!loading ? (
        <div className="mt-4 space-y-3">
          {suggestedAction ? (
            <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3 text-sm text-slate-100">
              {suggestedAction}
            </div>
          ) : (
            <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3 text-sm text-slate-500">—</div>
          )}

          {remediationSuggestions.length > 0 ? (
            <div>
              <div className="text-xs text-slate-400">Remediation Suggestions</div>
              <ul className="mt-2 list-disc pl-5 text-xs text-slate-300">
                {remediationSuggestions.map((s, idx) => (
                  <li key={idx}>{s}</li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}


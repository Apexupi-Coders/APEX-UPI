import React from 'react';

export default function LedgerPage() {
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Ledger Dashboard</div>
        <div className="mt-1 text-xs text-slate-400">
          Read-only ledger records. Wire ledger endpoints once operations-dashboard-api exposes them.
        </div>

        <div className="mt-4 overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="text-xs text-slate-400">
              <tr>
                <th className="px-3 py-2 font-medium">Ledger ID</th>
                <th className="px-3 py-2 font-medium">Transaction</th>
                <th className="px-3 py-2 font-medium">Amount</th>
                <th className="px-3 py-2 font-medium">State</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              <tr>
                <td className="px-3 py-4 text-xs text-slate-500" colSpan={4}>
                  No ledger rows available yet.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}


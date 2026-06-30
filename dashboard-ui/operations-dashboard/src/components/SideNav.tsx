import React from 'react';
import { navigate, routeOrder, RouteId, useRoute } from '../layouts/Router';

const label: Record<RouteId, string> = {
  executive: 'Overview',
  live: 'Live Txns',
  events: 'Event Stream',
  journey: 'Journey',
  kafka: 'Kafka Center',
  reconciliation: 'Reconciliation',
  ledger: 'Ledger',
  'root-cause': 'Root Cause Analysis',
  audit: 'Audit',
  errors: 'Errors',
  health: 'Health',
  architecture: 'Architecture',
  demo: 'Demo Mode',
};


export function SideNav() {
  const { route } = useRoute();

  return (
    <nav className="space-y-2">
      {routeOrder.map((r) => {
        const active = r === route;
        return (
          <button
            key={r}
            onClick={() => navigate(r)}
            className={
              active
                ? 'w-full rounded-xl bg-brand-500/15 ring-1 ring-brand-500/40 px-3 py-2 text-left text-sm font-semibold text-brand-200'
                : 'w-full rounded-xl border border-transparent px-3 py-2 text-left text-sm text-slate-300 hover:border-slate-800 hover:bg-slate-900/40'
            }
          >
            {label[r]}
          </button>
        );
      })}
    </nav>
  );
}


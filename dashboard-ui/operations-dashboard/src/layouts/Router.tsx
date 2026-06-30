import React, { useEffect, useState } from 'react';

export type RouteId =
  | 'executive'

  | 'live'
  | 'events'
  | 'journey'
  | 'kafka'
  | 'reconciliation'
  | 'ledger'
  | 'root-cause'
  | 'audit'
  | 'errors'
  | 'health'
  | 'architecture'
  | 'demo';

const routeOrder: RouteId[] = [
  'executive',
  'live',
  'events',
  'journey',
  'kafka',
  'reconciliation',
  'ledger',
  'root-cause',
  'audit',
  'errors',
  'health',
  'architecture',
  'demo',
];

const routeToHash: Record<RouteId, string> = {
  executive: '#/executive',
  live: '#/live',
  events: '#/events',
  journey: '#/journey',
  kafka: '#/kafka',
  reconciliation: '#/reconciliation',
  ledger: '#/ledger',
  'root-cause': '#/root-cause',
  audit: '#/audit',
  errors: '#/errors',
  health: '#/health',
  architecture: '#/architecture',
  demo: '#/demo',
};

export function useRoute() {
  const [route, setRoute] = useState<RouteId>('executive');

  useEffect(() => {
    const read = () => {
      const h = window.location.hash;
      const found = (Object.entries(routeToHash) as Array<[RouteId, string]>).find(([, v]) => v === h);
      setRoute(found?.[0] ?? 'executive');
    };
    read();
    window.addEventListener('hashchange', read);
    return () => window.removeEventListener('hashchange', read);
  }, []);

  return { route, routeOrder };
}

export function navigate(route: RouteId) {
  window.location.hash = routeToHash[route];
}

import { getPage } from '../pages/AllPages';

export function Outlet() {
  const { route } = useRoute();
  const Component = getPage(route);
  return <Component />;
}

export { routeOrder };






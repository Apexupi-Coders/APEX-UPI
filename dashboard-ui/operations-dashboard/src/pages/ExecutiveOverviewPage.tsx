import React, { useState, useMemo } from 'react';
import { usePolling } from '../hooks/usePolling';
import { fetchOverview } from '../services/opsApi';
import type { ServiceHealthStatus, OverviewResponse } from '../types/api';

type FilterType = 'ALL' | 'HEALTHY' | 'DOWN' | 'DEGRADED';

function StatusPill({ status }: { status: string }) {
  const normalized = status.toUpperCase();
  const color =
    normalized === 'UP' || normalized === 'OK'
      ? 'bg-emerald-500/20 text-emerald-200 ring-1 ring-emerald-400/30'
      : normalized === 'DOWN' || normalized === 'ERROR'
        ? 'bg-rose-500/20 text-rose-200 ring-1 ring-rose-400/30'
        : normalized === 'DEGRADED'
          ? 'bg-amber-500/20 text-amber-200 ring-1 ring-amber-400/30'
          : 'bg-slate-500/20 text-slate-200 ring-1 ring-slate-400/30';

  return <span className={`inline-flex rounded-full px-2 py-1 text-xs ${color}`}>{status}</span>;
}

function OverallStatusBadge({ status }: { status?: string }) {
  if (!status) return null;
  const normalized = status.toUpperCase();
  const color =
    normalized === 'UP'
      ? 'bg-emerald-500/20 text-emerald-200 ring-1 ring-emerald-400/30'
      : normalized === 'DEGRADED'
        ? 'bg-amber-500/20 text-amber-200 ring-1 ring-amber-400/30'
        : normalized === 'DOWN'
          ? 'bg-rose-500/20 text-rose-200 ring-1 ring-rose-400/30'
          : 'bg-slate-500/20 text-slate-200 ring-1 ring-slate-400/30';

  return <span className={`inline-flex rounded-full px-3 py-1 text-sm font-medium ${color}`}>{status}</span>;
}

function ServiceCard({ service }: { service: ServiceHealthStatus }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-950/30 p-3">
      <div className="flex items-center justify-between">
        <div className="text-xs font-medium text-slate-200">{service.serviceName}</div>
        <StatusPill status={service.status} />
      </div>
      <div className="mt-2 space-y-1">
        <div className="flex justify-between text-xs">
          <span className="text-slate-500">Latency</span>
          <span className="text-slate-300">{service.latencyMs >= 0 ? `${service.latencyMs} ms` : 'N/A'}</span>
        </div>
        <div className="flex justify-between text-xs">
          <span className="text-slate-500">HTTP Status</span>
          <span className="text-slate-300">{service.httpStatus ?? 'N/A'}</span>
        </div>
        <div className="flex justify-between text-xs">
          <span className="text-slate-500">Last Updated</span>
          <span className="text-slate-300">
            {service.timestamp ? new Date(service.timestamp).toLocaleTimeString() : 'N/A'}
          </span>
        </div>
        {service.errorMessage && (
          <div className="mt-1 text-xs text-rose-300">{service.errorMessage}</div>
        )}
      </div>
    </div>
  );
}

export default function ExecutiveOverviewPage() {
  const { data, error, loading } = usePolling(fetchOverview, { intervalMs: 5000, enabled: true });
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  const overview = data as OverviewResponse | null;
  const services = overview?.services ?? [];

  const filteredServices = useMemo(() => {
    let filtered = services;
    
    // Apply status filter
    if (filter !== 'ALL') {
      filtered = filtered.filter(s => {
        const status = s.status.toUpperCase();
        if (filter === 'HEALTHY') return status === 'UP';
        if (filter === 'DOWN') return status === 'DOWN' || status === 'ERROR';
        if (filter === 'DEGRADED') return status === 'DEGRADED';
        return true;
      });
    }
    
    // Apply search filter
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(s => s.serviceName.toLowerCase().includes(query));
    }
    
    return filtered;
  }, [services, filter, searchQuery]);

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <div className="text-sm font-semibold text-slate-200">Executive Overview</div>
              {overview?.overallStatus && <OverallStatusBadge status={overview.overallStatus} />}
            </div>
            <div className="mt-1 text-xs text-slate-400">
              Unified health of all backend VMs and services.
            </div>
          </div>
          <div className="text-right">
            <div className="text-xs text-slate-500">{loading ? 'Refreshing…' : overview?.timestamp ? new Date(overview.timestamp).toLocaleString() : ''}</div>
            {overview && (
              <div className="mt-1 flex gap-2 text-xs">
                <span className="text-emerald-300">UP: {overview.healthyCount}</span>
                {overview.degradedCount > 0 && <span className="text-amber-300">DEGRADED: {overview.degradedCount}</span>}
                <span className="text-rose-300">DOWN: {overview.unhealthyCount}</span>
                <span className="text-slate-400">Total: {overview.totalCount}</span>
              </div>
            )}
          </div>
        </div>

        {/* Filters */}
        <div className="mt-4 flex flex-wrap items-center gap-2">
          <div className="flex gap-1">
            {(['ALL', 'HEALTHY', 'DOWN', 'DEGRADED'] as FilterType[]).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`rounded px-2 py-1 text-xs ${
                  filter === f
                    ? 'bg-slate-700 text-slate-100'
                    : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                }`}
              >
                {f}
              </button>
            ))}
          </div>
          <input
            type="text"
            placeholder="Search services..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="rounded border border-slate-700 bg-slate-800 px-2 py-1 text-xs text-slate-200 placeholder-slate-500 focus:border-slate-600 focus:outline-none"
          />
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filteredServices.map((service) => (
            <ServiceCard key={service.serviceName} service={service} />
          ))}
          {filteredServices.length === 0 && (
            <div className="col-span-full text-center text-xs text-slate-500">No services match the current filter.</div>
          )}
        </div>

        {error ? <div className="mt-3 text-xs text-rose-200">{error.message}</div> : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Transaction Throughput</div>
          <div className="mt-2 text-xs text-slate-400">Wire later: counts + rates per state.</div>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Risk & Compliance</div>
          <div className="mt-2 text-xs text-slate-400">Wire later: security + validation failures.</div>
        </div>
      </div>
    </div>
  );
}


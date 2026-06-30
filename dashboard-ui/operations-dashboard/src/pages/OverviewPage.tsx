import React, { useEffect, useState } from 'react';
import { fetchOverview } from '../services/opsApi';
import type { OverviewResponse, ServiceHealthStatus } from '../types/api';

type Stat = {
  label: string;
  value: string;
};

function formatNumber(n: number): string {
  return n.toLocaleString();
}

function getServiceValue(services: ServiceHealthStatus[] | undefined, name: string): string {
  if (!services) return 'N/A';
  const service = services.find(s => s.serviceName === name);
  if (!service) return 'N/A';
  
  if (name === 'Total Transactions' || name === 'Ledger Entries' || name === 'Idempotency Keys') {
    return formatNumber(parseInt(service.errorMessage || '0'));
  }
  
  if (name.startsWith('Database') || name.startsWith('Cache') || name.startsWith('Messaging')) {
    return service.serviceName.split(' - ')[1] || 'N/A';
  }
  
  return service.serviceName;
}

export default function OverviewPage() {
  const [overview, setOverview] = useState<OverviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState<string>('never');

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        setLoading(true);
        setError(null);
        const data = await fetchOverview();
        if (!cancelled) {
          setOverview(data);
          setLastRefresh(new Date().toLocaleTimeString());
        }
      } catch (err) {
        if (!cancelled) {
          const message = err instanceof Error ? err.message : 'Failed to load overview';
          setError(message);
          console.error('Failed to load overview:', err);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    load();

    const interval = setInterval(load, 30000);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  const services = overview?.services || [];
  const isUnavailable = services.some(s => s.serviceName === 'LIVE SERVER UNAVAILABLE');

  const getTransactionValue = (name: string): string => {
    const service = services.find(s => s.serviceName === `Transactions - ${name}`);
    if (!service) return '0';
    return service.errorMessage || '0';
  };

  const transactionStats: Stat[] = [
    { label: 'SUCCESS', value: formatNumber(parseInt(getTransactionValue('SUCCESS'))) },
    { label: 'FAILED', value: formatNumber(parseInt(getTransactionValue('FAILED'))) },
    { label: 'SUBMITTED', value: formatNumber(parseInt(getTransactionValue('SUBMITTED'))) },
    { label: 'PENDING', value: formatNumber(parseInt(getTransactionValue('PENDING'))) },
  ];

  const infrastructureStats: Stat[] = [
    { label: 'Database', value: getServiceValue(services, 'Database') },
    { label: 'Cache', value: getServiceValue(services, 'Cache') },
    { label: 'Messaging', value: getServiceValue(services, 'Messaging') },
    { label: 'Crypto Enabled', value: services.find(s => s.serviceName === 'Crypto Enabled')?.status === 'UP' ? 'Yes' : 'No' },
  ];

  const serviceSizeStats: Stat[] = [
    { label: 'Total Transactions', value: getServiceValue(services, 'Total Transactions') },
    { label: 'Ledger Entries', value: getServiceValue(services, 'Ledger Entries') },
    { label: 'Idempotency Keys', value: getServiceValue(services, 'Idempotency Keys') },
  ];

  const failureToggleStats: Stat[] = [
    { label: 'NPCI Failure', value: services.find(s => s.serviceName === 'NPCI Failure Mode')?.status === 'UP' ? 'Normal' : 'Active' },
    { label: 'CBS Failure', value: services.find(s => s.serviceName === 'CBS Failure Mode')?.status === 'UP' ? 'Normal' : 'Active' },
    { label: 'Webhook Suppression', value: services.find(s => s.serviceName === 'Webhook Suppression')?.status === 'UP' ? 'Normal' : 'Active' },
  ];

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div>
            <div className="text-sm font-semibold text-slate-200">Operations Command Center</div>
            <div className="mt-1 text-xs text-slate-400">
              {isUnavailable ? 'LIVE SERVER UNAVAILABLE' : 'Live system status from control API'}
            </div>
          </div>
          <div className="text-xs text-slate-500">Last refresh: {lastRefresh}</div>
        </div>

        {error && (
          <div className="mt-4 rounded-lg border border-red-900/50 bg-red-950/30 p-3 text-xs text-red-200">
            {error}
          </div>
        )}

        {loading && !overview && (
          <div className="mt-4 text-xs text-slate-400">Loading live data...</div>
        )}

        {!loading && overview && services.length > 0 && (
          <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {transactionStats.map((s) => (
              <div key={s.label} className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
                <div className="text-xs text-slate-400">Transactions - {s.label}</div>
                <div className="mt-2 text-xl font-semibold text-slate-100">{s.value}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Infrastructure</div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {infrastructureStats.map((s) => (
              <div key={s.label} className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
                <div className="text-xs text-slate-400">{s.label}</div>
                <div className="mt-2 text-xl font-semibold text-slate-100">{s.value}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Service Sizes</div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {serviceSizeStats.map((s) => (
              <div key={s.label} className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
                <div className="text-xs text-slate-400">{s.label}</div>
                <div className="mt-2 text-xl font-semibold text-slate-100">{s.value}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">Failure Toggles</div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {failureToggleStats.map((s) => (
              <div key={s.label} className="rounded-lg border border-slate-800 bg-slate-950/20 p-4">
                <div className="text-xs text-slate-400">{s.label}</div>
                <div className="mt-2 text-xl font-semibold text-slate-100">{s.value}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="text-sm font-semibold text-slate-200">System Health</div>
          <div className="mt-4 space-y-2">
            <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/20 px-3 py-2">
              <div className="text-xs text-slate-300">Overall Status</div>
              <div className={`text-xs font-semibold ${isUnavailable ? 'text-red-200' : overview?.overallStatus === 'UP' ? 'text-emerald-200' : 'text-amber-200'}`}>
                {isUnavailable ? 'UNAVAILABLE' : (overview?.overallStatus || 'UNKNOWN')}
              </div>
            </div>
            <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/20 px-3 py-2">
              <div className="text-xs text-slate-300">Healthy Services</div>
              <div className="text-xs font-semibold text-slate-200">{overview?.healthyCount ?? 0}</div>
            </div>
            <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/20 px-3 py-2">
              <div className="text-xs text-slate-300">Degraded Services</div>
              <div className="text-xs font-semibold text-slate-200">{overview?.degradedCount ?? 0}</div>
            </div>
            <div className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/20 px-3 py-2">
              <div className="text-xs text-slate-300">Unhealthy Services</div>
              <div className="text-xs font-semibold text-slate-200">{overview?.unhealthyCount ?? 0}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
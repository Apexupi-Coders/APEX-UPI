import React from 'react';
import { usePolling } from '../hooks/usePolling';
import { fetchHealth } from '../services/opsApi';

export default function KafkaMonitoringPage() {
  const { data } = usePolling(fetchHealth, { intervalMs: 4000 });
  const kafkaStatus = data?.services?.Kafka?.status ?? '—';

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Kafka Monitoring Center</div>
        <div className="mt-1 text-xs text-slate-400">
          Monitoring-only visibility. Wire event counts once Kafka monitoring endpoints are added.
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3">
            <div className="text-xs text-slate-400">Kafka Broker</div>
            <div className="mt-2 text-sm text-brand-200">{kafkaStatus}</div>
          </div>
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3">
            <div className="text-xs text-slate-400">Outbound Consumer Lag</div>
            <div className="mt-2 text-sm text-slate-200">N/A</div>
          </div>
          <div className="rounded-lg border border-slate-800 bg-slate-950/20 p-3">
            <div className="text-xs text-slate-400">Inbound Consumer Lag</div>
            <div className="mt-2 text-sm text-slate-200">N/A</div>
          </div>
        </div>

        <div className="mt-4 text-xs text-slate-500">For full Kafka event stream and topic charts, extend operations-dashboard-api.</div>
      </div>
    </div>
  );
}


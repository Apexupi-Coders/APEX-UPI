import React from 'react';

export type DependencyChainNodeLabel = string;

export type DependencyChainNode = {
  /** Node id used for highlighting and mapping edges */
  id?: string;
  /** Display label (e.g., PSP, NPCI, Kafka, Redis, Ledger) */
  label?: DependencyChainNodeLabel;
  /** Optional status to infer highlight/failing */
  status?: string;
};

export type DependencyChainEdge = {
  from?: string;
  to?: string;
  label?: string;
};

export type DependencyChainGraph = {
  nodes?: DependencyChainNode[];
  edges?: DependencyChainEdge[];
  lastUpdatedAt?: string;
  /** If backend provides explicit failing node */
  failingNodeId?: string;
  /** Optional explicit failing label */
  failingNodeLabel?: string;
};

function normalizeLabel(s?: string): string {
  return (s ?? '').trim().toLowerCase();
}

function guessFailingNodeId(graph: DependencyChainGraph | undefined): string | undefined {
  if (!graph) return undefined;
  if (graph.failingNodeId) return graph.failingNodeId;
  if (graph.failingNodeLabel) {
    const wanted = normalizeLabel(graph.failingNodeLabel);
    const match = (graph.nodes ?? []).find((n) => normalizeLabel(n.label) === wanted);
    return match?.id;
  }

  // Heuristic: node with status containing FAIL
  const failNode = (graph.nodes ?? []).find((n) =>
    normalizeLabel(n.status).includes('fail'),
  );
  return failNode?.id;
}

function renderArrow({ kind }: { kind?: 'normal' | 'failing' }) {
  const isFail = kind === 'failing';
  return (
    <div className="mx-2 flex items-center" aria-hidden="true">
      <span className={`text-lg leading-none ${isFail ? 'text-rose-300' : 'text-slate-400'}`}>↓</span>
    </div>
  );
}

function renderNode({
  label,
  isFailing,
}: {
  label: string;
  isFailing?: boolean;
}) {
  return (
    <div
      className={
        'flex items-center justify-center rounded-lg border px-3 py-2 text-xs font-semibold ' +
        (isFailing
          ? 'border-rose-400/60 bg-rose-400/10 text-rose-200'
          : 'border-slate-800 bg-slate-950/20 text-slate-200')
      }
      title={label}
    >
      {label}
    </div>
  );
}

/**
 * Minimal “cards + arrows” chain visualization.
 *
 * Supports two shapes:
 * - Backend returns a DependencyGraph { nodes, edges }
 * - Backend returns already-ordered nodes (PSP -> ... )
 */
export default function DependencyChainVisualization({
  dependencyGraph,
  loading,
}: {
  dependencyGraph?: DependencyChainGraph;
  loading?: boolean;
}) {
  const graph = dependencyGraph;

  const failingNodeId = guessFailingNodeId(graph);

  const orderedLabels: string[] = React.useMemo(() => {
    // If backend nodes are already in correct order (common for a chain), just use node labels.
    const nodes = graph?.nodes ?? [];
    const labels = nodes.map((n) => n.label).filter(Boolean) as string[];
    if (labels.length >= 2) return labels;

    // Otherwise try to derive a path from edges.
    const edges = graph?.edges ?? [];
    if (nodes.length === 0 || edges.length === 0) return [];

    const byId = new Map((nodes ?? []).map((n) => [n.id, n] as const));
    const outgoing = new Map<string, string[]>();
    const incoming = new Map<string, number>();

    for (const e of edges) {
      if (!e.from || !e.to) continue;
      outgoing.set(e.from, [...(outgoing.get(e.from) ?? []), e.to]);
      incoming.set(e.to, (incoming.get(e.to) ?? 0) + 1);
    }

    // Find start: node with no incoming
    const start = (nodes ?? []).find((n) => n.id && (incoming.get(n.id) ?? 0) === 0)?.id;
    if (!start) return [];

    const pathIds: string[] = [start];
    const visited = new Set<string>([start]);
    let cur = start;

    // Follow first edge to keep it simple.
    for (let i = 0; i < 10; i++) {
      const nextCandidates = outgoing.get(cur) ?? [];
      const next = nextCandidates.find((id) => !visited.has(id));
      if (!next) break;
      pathIds.push(next);
      visited.add(next);
      cur = next;
    }

    return pathIds
      .map((id) => byId.get(id)?.label)
      .filter(Boolean) as string[];
  }, [graph]);

  if (loading) {
    return (
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Dependency Chain</div>
        <div className="mt-2 text-xs text-slate-500">Building dependency chain…</div>
      </div>
    );
  }

  if (!orderedLabels.length) {
    return (
      <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
        <div className="text-sm font-semibold text-slate-200">Dependency Chain</div>
        <div className="mt-3 text-xs text-slate-500">No dependency chain available.</div>
      </div>
    );
  }

  // Highlight by failing node id where possible.
  const idToLabel = new Map<string, string>(
    (graph?.nodes ?? [])
      .map((n) => [n.id, n.label] as const)
      .filter(([id, label]) => !!id && !!label)
      .map(([id, label]) => [id as string, label as string])
  );

  const failingLabel = failingNodeId
    ? (idToLabel.get(failingNodeId) as string | undefined)
    : undefined;


  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="text-sm font-semibold text-slate-200">Dependency Chain</div>
      <div className="mt-2 text-xs text-slate-400">Visual dependency chain causing the failure.</div>

      <div className="mt-4 flex items-center justify-start">
        {orderedLabels.map((label, idx) => {
          const isFailing =
            failingNodeId
              ? normalizeLabel(label) === normalizeLabel(failingLabel)
              : normalizeLabel(label) === normalizeLabel(graph?.failingNodeLabel);

          return (
            <React.Fragment key={`${label}-${idx}`}>
              {renderNode({ label, isFailing })}
              {idx < orderedLabels.length - 1 ? renderArrow({ kind: isFailing ? 'failing' : 'normal' }) : null}
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
}


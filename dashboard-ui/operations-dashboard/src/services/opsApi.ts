import type {
  OpsHealthResponse,
  OpsSearchResponse,
  TransactionJourneyDto,
} from '../types/api';

const BASE_URL = 'http://localhost:8081';

async function getJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'GET',
    ...init,
    headers: {
      'Accept': 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`GET ${path} failed: ${res.status} ${res.statusText} ${text}`);
  }

  return (await res.json()) as T;
}

export async function fetchHealth(): Promise<OpsHealthResponse> {
  return getJson<OpsHealthResponse>('/api/v1/ops/health');
}

export async function fetchJourneyByTid(tid: string): Promise<TransactionJourneyDto> {
  return getJson<TransactionJourneyDto>(`/api/v1/ops/transactions/${encodeURIComponent(tid)}/journey`);
}

export async function searchTransactions(tr?: string, pa?: string): Promise<OpsSearchResponse> {
  const params = new URLSearchParams();
  if (tr) params.set('tr', tr);
  if (pa) params.set('pa', pa);

  return getJson<OpsSearchResponse>(`/api/v1/ops/transactions/search?${params.toString()}`);
}


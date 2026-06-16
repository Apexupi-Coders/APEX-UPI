import { useEffect, useRef, useState } from 'react';

export function usePolling<T>(
  fn: () => Promise<T>,
  options?: {
    intervalMs?: number;
    enabled?: boolean;
  }
) {
  const intervalMs = options?.intervalMs ?? 3000;
  const enabled = options?.enabled ?? true;
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!enabled) return;

    let timer: number | undefined;

    const run = async () => {
      try {
        setLoading(true);
        setError(null);
        const res = await fn();
        if (!mounted.current) return;
        setData(res);
      } catch (e) {
        if (!mounted.current) return;
        setError(e instanceof Error ? e : new Error(String(e)));
      } finally {
        if (!mounted.current) return;
        setLoading(false);
      }
    };

    run();
    timer = window.setInterval(run, intervalMs);

    return () => {
      if (timer) window.clearInterval(timer);
    };
  }, [enabled, fn, intervalMs]);

  return { data, error, loading };
}


import { useCallback, useEffect, useRef, useState } from 'react';

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  reload: () => void;
}

/**
 * Loads async data for the given deps.
 * Soft reloads (reload()) keep prior data visible — no skeleton flash.
 * Dependency identity changes clear data and show loading again.
 */
export function useAsync<T>(
  factory: () => Promise<T>,
  deps: unknown[] = [],
): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [tick, setTick] = useState(0);
  const hasDataRef = useRef(false);
  const depsKey = JSON.stringify(deps);
  const prevDepsKey = useRef(depsKey);

  const reload = useCallback(() => setTick((n) => n + 1), []);

  useEffect(() => {
    const depsChanged = prevDepsKey.current !== depsKey;
    prevDepsKey.current = depsKey;

    if (depsChanged) {
      hasDataRef.current = false;
      setData(null);
    }

    let cancelled = false;
    const soft = hasDataRef.current;
    if (!soft) {
      setLoading(true);
    }
    setError(null);

    factory()
      .then((result) => {
        if (!cancelled) {
          setData(result);
          hasDataRef.current = true;
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err : new Error(String(err)));
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [depsKey, tick]);

  return { data, loading, error, reload };
}

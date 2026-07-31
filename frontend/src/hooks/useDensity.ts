import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
  createElement,
} from 'react';

export type Density = 'comfortable' | 'compact';

const STORAGE_KEY = 'vox-density';

interface DensityContextValue {
  density: Density;
  setDensity: (d: Density) => void;
  toggleDensity: () => void;
  isCompact: boolean;
}

const DensityContext = createContext<DensityContextValue | null>(null);

function readStored(): Density {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    if (v === 'compact' || v === 'comfortable') return v;
  } catch {
    /* ignore */
  }
  return 'comfortable';
}

export function DensityProvider({ children }: { children: ReactNode }) {
  const [density, setDensityState] = useState<Density>(readStored);

  const setDensity = useCallback((d: Density) => {
    setDensityState(d);
    try {
      localStorage.setItem(STORAGE_KEY, d);
    } catch {
      /* ignore */
    }
  }, []);

  const toggleDensity = useCallback(() => {
    setDensity(density === 'compact' ? 'comfortable' : 'compact');
  }, [density, setDensity]);

  useEffect(() => {
    document.documentElement.dataset.density = density;
  }, [density]);

  const value = useMemo(
    () => ({
      density,
      setDensity,
      toggleDensity,
      isCompact: density === 'compact',
    }),
    [density, setDensity, toggleDensity],
  );

  return createElement(DensityContext.Provider, { value }, children);
}

export function useDensity(): DensityContextValue {
  const ctx = useContext(DensityContext);
  if (!ctx) throw new Error('useDensity must be used within DensityProvider');
  return ctx;
}

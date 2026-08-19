import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { LocaleCode } from '@/types';
import { fetchLocalePreference, updateLocalePreference } from '@/api/locale';
import { getApiToken, isMockMode, onApiTokenChange } from '@/api/client';
import { isOidcEnabled } from '@/auth/config';
import ru from './locales/ru.json';
import en from './locales/en.json';
import de from './locales/de.json';

const catalogs: Record<LocaleCode, MessageTree> = {
  ru: ru as MessageTree,
  en: en as MessageTree,
  de: de as MessageTree,
};

export const SUPPORTED_LOCALES: LocaleCode[] = ['ru', 'en', 'de'];
export const DEFAULT_LOCALE: LocaleCode = 'ru';
const STORAGE_KEY = 'vox-locale';

type MessageTree = { [key: string]: string | MessageTree };

type Vars = Record<string, string | number>;

interface I18nContextValue {
  locale: LocaleCode;
  setLocale: (locale: LocaleCode) => void;
  t: (key: string, vars?: Vars) => string;
  locales: LocaleCode[];
}

const I18nContext = createContext<I18nContextValue | null>(null);

function getByPath(tree: MessageTree, key: string): string | undefined {
  const parts = key.split('.');
  let node: string | MessageTree | undefined = tree;
  for (const part of parts) {
    if (node == null || typeof node === 'string') return undefined;
    node = node[part];
  }
  return typeof node === 'string' ? node : undefined;
}

function interpolate(template: string, vars?: Vars): string {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (_, name: string) =>
    vars[name] !== undefined ? String(vars[name]) : `{${name}}`,
  );
}

function readStoredLocale(): LocaleCode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY) as LocaleCode | null;
    if (stored && SUPPORTED_LOCALES.includes(stored)) return stored;
  } catch {
    /* ignore */
  }
  return DEFAULT_LOCALE;
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<LocaleCode>(readStoredLocale);

  // Live mode: hydrate from GET /api/v1/me/locale once a bearer token exists.
  // Calling it while anonymous only produces a 401 and races the OIDC restore.
  useEffect(() => {
    if (isMockMode()) return;
    let cancelled = false;
    let hydrated = false;
    const hydrate = () => {
      if (cancelled || hydrated) return;
      if (isOidcEnabled() && !getApiToken()) return;
      hydrated = true;
      void fetchLocalePreference().then((remote) => {
        if (cancelled || !remote) return;
        setLocaleState(remote);
        try {
          localStorage.setItem(STORAGE_KEY, remote);
        } catch {
          /* ignore */
        }
      });
    };
    hydrate();
    const unsubscribe = onApiTokenChange(hydrate);
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  const setLocale = useCallback((next: LocaleCode) => {
    if (!SUPPORTED_LOCALES.includes(next)) return;
    setLocaleState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* ignore */
    }
    if (!isMockMode()) {
      void updateLocalePreference(next);
    }
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const t = useCallback(
    (key: string, vars?: Vars) => {
      const value =
        getByPath(catalogs[locale], key) ??
        getByPath(catalogs[DEFAULT_LOCALE], key) ??
        key;
      return interpolate(value, vars);
    },
    [locale],
  );

  const value = useMemo(
    () => ({ locale, setLocale, t, locales: SUPPORTED_LOCALES }),
    [locale, setLocale, t],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used within I18nProvider');
  return ctx;
}

export function useT() {
  return useI18n().t;
}

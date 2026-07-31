import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { getApiToken, setAuthRefreshHandler } from '@/api/client';
import { getOidcConfig, isOidcEnabled } from './config';
import {
  clearPkceSession,
  consumePkceSession,
  createPkceLoginParams,
  storePkceSession,
} from './pkce';
import {
  readSession,
  sessionFromTokenResponse,
  writeSession,
  type AuthUser,
  type OidcSession,
  type TokenResponse,
} from './session';

export interface AuthContextValue {
  /** OIDC is configured and VITE_OIDC_ENABLED=true. */
  oidcEnabled: boolean;
  /** Hydration / callback exchange in progress. */
  loading: boolean;
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  error: string | null;
  login: (returnTo?: string) => Promise<void>;
  logout: () => Promise<void>;
  handleCallback: (search: string) => Promise<string | null>;
  clearError: () => void;
  /** True when live API has no Bearer token (soft prompt). */
  needsSignIn: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

async function postToken(
  tokenEndpoint: string,
  body: Record<string, string>,
): Promise<TokenResponse> {
  const res = await fetch(tokenEndpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(body).toString(),
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const err = (await res.json()) as { error_description?: string; error?: string };
      detail = err.error_description || err.error || detail;
    } catch {
      /* ignore */
    }
    throw new Error(detail || `Token request failed (${res.status})`);
  }
  return (await res.json()) as TokenResponse;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const oidcEnabled = isOidcEnabled();
  const [session, setSession] = useState<OidcSession | null>(null);
  const [loading, setLoading] = useState(oidcEnabled);
  const [error, setError] = useState<string | null>(null);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const sessionRef = useRef<OidcSession | null>(null);

  const applySession = useCallback((next: OidcSession | null) => {
    sessionRef.current = next;
    writeSession(next);
    setSession(next);
  }, []);

  const refreshTokens = useCallback(async (): Promise<boolean> => {
    const cfg = getOidcConfig();
    // Prefer live ref; fall back to sessionStorage (e.g. race before hydrate)
    const current = sessionRef.current ?? readSession();
    if (!cfg || !current?.refreshToken) return false;
    if (!sessionRef.current) {
      sessionRef.current = current;
    }
    try {
      const tokens = await postToken(cfg.tokenEndpoint, {
        grant_type: 'refresh_token',
        client_id: cfg.clientId,
        refresh_token: current.refreshToken,
      });
      // Preserve refresh_token if rotation is not used
      const next = sessionFromTokenResponse({
        ...tokens,
        refresh_token: tokens.refresh_token ?? current.refreshToken,
      });
      applySession(next);
      return true;
    } catch {
      applySession(null);
      return false;
    }
  }, [applySession]);

  // Wire 401 interceptor → single-flight refresh for apiRequest / apiFetch
  useEffect(() => {
    if (!oidcEnabled) {
      setAuthRefreshHandler(null);
      return;
    }
    setAuthRefreshHandler(() => refreshTokens());
    return () => setAuthRefreshHandler(null);
  }, [oidcEnabled, refreshTokens]);

  const scheduleRefresh = useCallback(
    (s: OidcSession | null) => {
      if (refreshTimer.current) {
        clearTimeout(refreshTimer.current);
        refreshTimer.current = null;
      }
      if (!s?.refreshToken) return;
      // Refresh slightly before expiresAt (already skewed in sessionFromTokenResponse)
      const delay = Math.max(5_000, s.expiresAt - Date.now());
      refreshTimer.current = setTimeout(() => {
        void refreshTokens().then((ok) => {
          if (ok) scheduleRefresh(sessionRef.current);
        });
      }, delay);
    },
    [refreshTokens],
  );

  // Hydrate from sessionStorage once on mount
  const hydrated = useRef(false);
  useEffect(() => {
    if (hydrated.current) return;
    hydrated.current = true;
    if (!oidcEnabled) {
      setLoading(false);
      return;
    }
    const existing = readSession();
    if (existing) {
      // Ensure api token mirror is in sync
      writeSession(existing);
      sessionRef.current = existing;
      setSession(existing);
      if (existing.expiresAt <= Date.now() && existing.refreshToken) {
        void refreshTokens().finally(() => setLoading(false));
        return;
      }
      // Near expiry on load: refresh if within 90s of expiry
      if (existing.refreshToken && existing.expiresAt - Date.now() < 90_000) {
        void refreshTokens().finally(() => setLoading(false));
        return;
      }
    }
    setLoading(false);
  }, [oidcEnabled, refreshTokens]);

  useEffect(() => {
    scheduleRefresh(session);
    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
    };
  }, [session, scheduleRefresh]);

  // Silent renew when tab becomes visible or window gains focus
  useEffect(() => {
    if (!oidcEnabled) return;
    const maybeRefresh = () => {
      const s = sessionRef.current;
      if (!s?.refreshToken) return;
      if (s.expiresAt - Date.now() < 120_000) {
        void refreshTokens();
      }
    };
    const onVis = () => {
      if (document.visibilityState === 'visible') maybeRefresh();
    };
    document.addEventListener('visibilitychange', onVis);
    window.addEventListener('focus', maybeRefresh);
    return () => {
      document.removeEventListener('visibilitychange', onVis);
      window.removeEventListener('focus', maybeRefresh);
    };
  }, [oidcEnabled, refreshTokens]);

  // Cross-tab logout / login sync via storage events on token mirror
  useEffect(() => {
    if (!oidcEnabled) return;
    const onStorage = (e: StorageEvent) => {
      if (e.key !== 'vox-api-token') return;
      if (e.newValue == null || e.newValue === '') {
        sessionRef.current = null;
        setSession(null);
      } else if (e.newValue && e.newValue !== sessionRef.current?.accessToken) {
        const restored = readSession();
        if (restored) {
          sessionRef.current = restored;
          setSession(restored);
        }
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [oidcEnabled]);

  const login = useCallback(async (returnTo?: string) => {
    const cfg = getOidcConfig();
    if (!cfg) {
      setError('OIDC is not configured');
      return;
    }
    setError(null);
    const { codeVerifier, codeChallenge, state } = await createPkceLoginParams();
    storePkceSession(
      codeVerifier,
      state,
      returnTo ??
        (typeof window !== 'undefined'
          ? `${window.location.pathname}${window.location.search}`
          : '/'),
    );
    const url = new URL(cfg.authorizationEndpoint);
    url.searchParams.set('client_id', cfg.clientId);
    url.searchParams.set('redirect_uri', cfg.redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('scope', cfg.scopes);
    url.searchParams.set('state', state);
    url.searchParams.set('code_challenge', codeChallenge);
    url.searchParams.set('code_challenge_method', 'S256');
    window.location.assign(url.toString());
  }, []);

  const logout = useCallback(async () => {
    const cfg = getOidcConfig();
    const idToken = sessionRef.current?.idToken;
    clearPkceSession();
    applySession(null);
    setError(null);
    if (cfg && idToken) {
      const url = new URL(cfg.endSessionEndpoint);
      url.searchParams.set('id_token_hint', idToken);
      url.searchParams.set(
        'post_logout_redirect_uri',
        typeof window !== 'undefined' ? window.location.origin : cfg.redirectUri,
      );
      window.location.assign(url.toString());
      return;
    }
    if (cfg) {
      // No id_token — still clear local session; optional RP-initiated without hint
      const url = new URL(cfg.endSessionEndpoint);
      url.searchParams.set(
        'post_logout_redirect_uri',
        typeof window !== 'undefined' ? window.location.origin : cfg.redirectUri,
      );
      url.searchParams.set('client_id', cfg.clientId);
      window.location.assign(url.toString());
    }
  }, [applySession]);

  const handleCallback = useCallback(
    async (search: string): Promise<string | null> => {
      const cfg = getOidcConfig();
      if (!cfg) {
        setError('OIDC is not configured');
        return null;
      }
      setLoading(true);
      setError(null);
      try {
        const params = new URLSearchParams(
          search.startsWith('?') ? search.slice(1) : search,
        );
        const err = params.get('error');
        if (err) {
          throw new Error(params.get('error_description') || err);
        }
        const code = params.get('code');
        const state = params.get('state');
        if (!code) {
          throw new Error('Missing authorization code');
        }
        const pkce = consumePkceSession();
        if (!pkce.state || !state || pkce.state !== state) {
          throw new Error('Invalid OAuth state');
        }
        if (!pkce.codeVerifier) {
          throw new Error('Missing PKCE verifier');
        }
        const tokens = await postToken(cfg.tokenEndpoint, {
          grant_type: 'authorization_code',
          client_id: cfg.clientId,
          code,
          redirect_uri: cfg.redirectUri,
          code_verifier: pkce.codeVerifier,
        });
        const next = sessionFromTokenResponse(tokens);
        applySession(next);
        return pkce.returnTo || '/';
      } catch (e) {
        clearPkceSession();
        applySession(null);
        const message = e instanceof Error ? e.message : 'Login failed';
        setError(message);
        return null;
      } finally {
        setLoading(false);
      }
    },
    [applySession],
  );

  const clearError = useCallback(() => setError(null), []);

  const accessToken = session?.accessToken ?? null;
  const isAuthenticated = Boolean(accessToken);
  // Soft sign-in: live API, OIDC on, no token (env token or session)
  const needsSignIn =
    oidcEnabled &&
    import.meta.env.VITE_USE_MOCK === 'false' &&
    !getApiToken() &&
    !isAuthenticated;

  const value = useMemo<AuthContextValue>(
    () => ({
      oidcEnabled,
      loading,
      user: session?.user ?? null,
      accessToken,
      isAuthenticated,
      error,
      login,
      logout,
      handleCallback,
      clearError,
      needsSignIn,
    }),
    [
      oidcEnabled,
      loading,
      session,
      accessToken,
      isAuthenticated,
      error,
      login,
      logout,
      handleCallback,
      clearError,
      needsSignIn,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

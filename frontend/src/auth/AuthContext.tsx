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
import { getApiToken, setAuthRefreshHandler, isMockMode } from '@/api/client';
import { getOidcConfig, isOidcEnabled } from './config';
import {
  clearPkceSession,
  consumePkceSession,
  createPkceLoginParams,
  peekPkceSession,
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

/** Authorization-server answers to prompt=none that simply mean "not signed in". */
const SILENT_RESTORE_DECLINED = new Set([
  'login_required',
  'interaction_required',
  'consent_required',
  'account_selection_required',
]);

const SILENT_RESTORE_KEY = 'vox-itsm.oidc.silent-restore';
/** Set once this browser has held a session, so first-time visitors are never redirected. */
const HAD_SESSION_KEY = 'vox-itsm.oidc.had-session';
/** Marks the authorization request currently in flight as a silent one. */
const SILENT_PENDING_KEY = 'vox-itsm.oidc.silent-pending';

function silentRestoreTried(): boolean {
  try {
    return sessionStorage.getItem(SILENT_RESTORE_KEY) === '1';
  } catch {
    return true;
  }
}

function markSilentRestoreTried(): void {
  try {
    sessionStorage.setItem(SILENT_RESTORE_KEY, '1');
  } catch {
    /* ignore */
  }
}

function rememberHadSession(had: boolean): void {
  try {
    if (had) localStorage.setItem(HAD_SESSION_KEY, '1');
    else localStorage.removeItem(HAD_SESSION_KEY);
  } catch {
    /* ignore */
  }
}

function hadSession(): boolean {
  try {
    return localStorage.getItem(HAD_SESSION_KEY) === '1';
  } catch {
    return false;
  }
}

function markSilentPending(pending: boolean): void {
  try {
    if (pending) sessionStorage.setItem(SILENT_PENDING_KEY, '1');
    else sessionStorage.removeItem(SILENT_PENDING_KEY);
  } catch {
    /* ignore */
  }
}

function consumeSilentPending(): boolean {
  try {
    const pending = sessionStorage.getItem(SILENT_PENDING_KEY) === '1';
    sessionStorage.removeItem(SILENT_PENDING_KEY);
    return pending;
  } catch {
    return false;
  }
}

/** Allow one silent restore again once a real session exists (survives reloads). */
function resetSilentRestore(): void {
  try {
    sessionStorage.removeItem(SILENT_RESTORE_KEY);
  } catch {
    /* ignore */
  }
}

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
    if (next) {
      resetSilentRestore();
      rememberHadSession(true);
    }
  }, []);

  const refreshTokens = useCallback(async (): Promise<boolean> => {
    const cfg = getOidcConfig();
    // Prefer live ref; fall back to current in-memory session.
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

  const startAuthorization = useCallback(
    async (returnTo: string | undefined, silent: boolean) => {
      const cfg = getOidcConfig();
      if (!cfg) {
        if (!silent) setError('OIDC is not configured');
        return;
      }
      if (!silent) setError(null);
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
      // Silent restore rides the identity provider session cookie; it never shows a form,
      // and it must never surface an error if the round trip does not work out.
      markSilentPending(silent);
      if (silent) url.searchParams.set('prompt', 'none');
      window.location.assign(url.toString());
    },
    [],
  );

  // Hydrate current in-memory session once on mount.
  const hydrated = useRef(false);
  useEffect(() => {
    if (hydrated.current) return;
    hydrated.current = true;
    if (!oidcEnabled) {
      setLoading(false);
      return;
    }
    const existing = readSession();
    if (!existing && typeof window !== 'undefined'
        && !window.location.pathname.startsWith('/auth/callback')
        && hadSession()
        && !silentRestoreTried()) {
      // Tokens are never persisted, so a reload starts anonymous. Ask the identity
      // provider once per tab whether an SSO session exists and restore it without a form.
      markSilentRestoreTried();
      void startAuthorization(
        `${window.location.pathname}${window.location.search}`,
        true,
      );
      return;
    }
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
  }, [oidcEnabled, refreshTokens, startAuthorization]);

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

  const login = useCallback(
    async (returnTo?: string) => startAuthorization(returnTo, false),
    [startAuthorization],
  );

  const logout = useCallback(async () => {
    const cfg = getOidcConfig();
    const idToken = sessionRef.current?.idToken;
    clearPkceSession();
    applySession(null);
    // Explicit sign-out must not be undone by a silent restore on the next load.
    markSilentRestoreTried();
    rememberHadSession(false);
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
      const wasSilent = consumeSilentPending();
      let pkceReturnTo: string | null = null;
      const clearPkceSessionReturnTo = () => pkceReturnTo;
      try {
        const params = new URLSearchParams(
          search.startsWith('?') ? search.slice(1) : search,
        );
        const err = params.get('error');
        const state = params.get('state');
        if (err && SILENT_RESTORE_DECLINED.has(err)) {
          // prompt=none answered "no active session" — stay anonymous, no error banner.
          const declined = peekPkceSession();
          if (declined.state && state && declined.state === state) {
            consumePkceSession();
          }
          applySession(null);
          return declined.returnTo || '/';
        }
        if (err) {
          throw new Error(params.get('error_description') || err);
        }
        const code = params.get('code');
        if (!code) {
          throw new Error('Missing authorization code');
        }
        // A callback can arrive stale: the silent restore and an interactive login can
        // overlap, and one redirect can be replayed. Look before consuming, so a stale
        // answer neither destroys the authorization still in flight nor reports a failure.
        const pending = peekPkceSession();
        pkceReturnTo = pending.returnTo;
        if (!state || !pending.state || pending.state !== state) {
          return pending.returnTo || '/';
        }
        const pkce = consumePkceSession();
        pkceReturnTo = pkce.returnTo;
        if (!pkce.codeVerifier) {
          if (sessionRef.current !== null) return pkce.returnTo || '/';
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
        const returnTo = clearPkceSessionReturnTo();
        clearPkceSession();
        applySession(null);
        if (wasSilent) {
          // A silent restore that cannot complete simply leaves the user anonymous.
          return returnTo || '/';
        }
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
    !isMockMode() &&
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

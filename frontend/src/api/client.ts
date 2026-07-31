/**
 * HTTP client for /api/v1 backend.
 * Routes through mock layer when VITE_USE_MOCK !== 'false' (default: mock on).
 *
 * On HTTP 401: single-flight OIDC refresh (if registered) then one retry.
 */

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiErrorBody {
  message: string;
  code?: string;
  details?: unknown;
}

export class ApiError extends Error {
  status: number;
  body?: ApiErrorBody;

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

const DEFAULT_BASE = '/api/v1';
const TOKEN_STORAGE_KEY = 'vox-api-token';
const ACTOR_STORAGE_KEY = 'vox-user-id';

/** Returns true if tokens were refreshed and a retry may succeed. */
export type AuthRefreshHandler = () => Promise<boolean>;

let authRefreshHandler: AuthRefreshHandler | null = null;
/** Coalesce concurrent 401s into one refresh. */
let refreshInFlight: Promise<boolean> | null = null;

/**
 * AuthProvider registers OIDC silent refresh here.
 * Pass null on unmount.
 */
export function setAuthRefreshHandler(handler: AuthRefreshHandler | null): void {
  authRefreshHandler = handler;
}

async function tryAuthRefresh(): Promise<boolean> {
  if (!authRefreshHandler) return false;
  if (!refreshInFlight) {
    refreshInFlight = authRefreshHandler().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export function getBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE as string | undefined) ?? DEFAULT_BASE;
}

/** Mock is default; set VITE_USE_MOCK=false for live backend. */
export function useMock(): boolean {
  return import.meta.env.VITE_USE_MOCK !== 'false';
}

export function getApiToken(): string | null {
  const fromEnv = (import.meta.env.VITE_API_TOKEN as string | undefined)?.trim();
  if (fromEnv) return fromEnv;
  try {
    const stored = localStorage.getItem(TOKEN_STORAGE_KEY);
    return stored?.trim() || null;
  } catch {
    return null;
  }
}

export function setApiToken(token: string | null): void {
  try {
    if (token == null || token === '') {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    } else {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    }
  } catch {
    /* ignore quota / private mode */
  }
}

/**
 * Subject used for "assign to me" when no users API is available.
 * Prefer localStorage `vox-user-id`, else dev-local (Spring profile dev).
 */
export function getApiActorId(): string {
  try {
    const stored = localStorage.getItem(ACTOR_STORAGE_KEY)?.trim();
    if (stored) return stored;
  } catch {
    /* ignore */
  }
  return (import.meta.env.VITE_API_ACTOR as string | undefined)?.trim() || 'dev-local';
}

/** Persist OIDC `sub` (or clear) for assign-to-me and actor headers. */
export function setApiActorId(actorId: string | null): void {
  try {
    if (actorId == null || actorId === '') {
      localStorage.removeItem(ACTOR_STORAGE_KEY);
    } else {
      localStorage.setItem(ACTOR_STORAGE_KEY, actorId);
    }
  } catch {
    /* ignore quota / private mode */
  }
}

export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  /** Skip 401 refresh+retry (internal / special calls). */
  skipAuthRefresh?: boolean;
}

function buildUrl(path: string): string {
  return `${getBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`;
}

function authHeaders(): Record<string, string> {
  const token = getApiToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function parseErrorBody(res: Response): Promise<ApiErrorBody | undefined> {
  try {
    return (await res.json()) as ApiErrorBody;
  } catch {
    return undefined;
  }
}

/**
 * Low-level fetch with Bearer + optional 401 refresh/retry.
 * Use for multipart uploads (do not set Content-Type).
 */
export async function apiFetch(
  path: string,
  init: RequestInit & { skipAuthRefresh?: boolean } = {},
  retried = false,
): Promise<Response> {
  const { skipAuthRefresh, headers: initHeaders, ...rest } = init;
  const headers = new Headers(initHeaders);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  const token = getApiToken();
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const res = await fetch(buildUrl(path), {
    ...rest,
    headers,
  });

  if (
    res.status === 401 &&
    !retried &&
    !skipAuthRefresh &&
    !useMock()
  ) {
    const refreshed = await tryAuthRefresh();
    if (refreshed) {
      return apiFetch(path, init, true);
    }
  }

  return res;
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
  retried = false,
): Promise<T> {
  const { method = 'GET', body, headers = {}, signal, skipAuthRefresh } = options;

  const res = await fetch(buildUrl(path), {
    method,
    signal,
    headers: {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...authHeaders(),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (
    res.status === 401 &&
    !retried &&
    !skipAuthRefresh &&
    !useMock()
  ) {
    const refreshed = await tryAuthRefresh();
    if (refreshed) {
      return apiRequest<T>(path, options, true);
    }
  }

  if (!res.ok) {
    const errBody = await parseErrorBody(res);
    throw new ApiError(res.status, errBody?.message ?? res.statusText, errBody);
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

/** Simulate network latency for mock mode */
export function delay(ms = 280): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

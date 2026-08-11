/**
 * HTTP client for /api/v1 backend.
 * Routes through mock layer only when VITE_USE_MOCK=true (default: live API).
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
let apiToken: string | null = null;
let apiActorId: string | null = null;

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

/** Live API is default. Mock data requires explicit VITE_USE_MOCK=true. */
export function isMockMode(): boolean {
  return import.meta.env.VITE_USE_MOCK === 'true';
}

/** Live bulk / CMS feature not wired to server — never fake success. */
export const LIVE_FEATURE_UNSUPPORTED = 'LIVE_FEATURE_UNSUPPORTED';

/**
 * Throw when a write path is mock-only (bulk assign, KB CMS, etc.).
 * Callers toast `errorKey` (i18n) — never report fake success counts.
 */
export function refuseLiveFeature(errorKey: string): never {
  throw new ApiError(501, errorKey, {
    message: errorKey,
    code: LIVE_FEATURE_UNSUPPORTED,
  });
}

export function isLiveFeatureUnsupported(err: unknown): boolean {
  return err instanceof ApiError && err.body?.code === LIVE_FEATURE_UNSUPPORTED;
}

export function getApiToken(): string | null {
  const fromEnv = import.meta.env.DEV
    ? (import.meta.env.VITE_API_TOKEN as string | undefined)?.trim()
    : undefined;
  if (fromEnv) return fromEnv;
  return apiToken;
}

export function setApiToken(token: string | null): void {
  apiToken = token?.trim() || null;
}

/**
 * Subject used for "assign to me" when no users API is available.
 * OIDC subject stays memory-only with tokens; dev profile falls back to dev-local.
 */
export function getApiActorId(): string {
  if (apiActorId) return apiActorId;
  return (import.meta.env.VITE_API_ACTOR as string | undefined)?.trim() || 'dev-local';
}

/** Keep OIDC `sub` in memory for assign-to-me; clear atomically with bearer session. */
export function setApiActorId(actorId: string | null): void {
  apiActorId = actorId?.trim() || null;
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
    !isMockMode()
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
    !isMockMode()
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

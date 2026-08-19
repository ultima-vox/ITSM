/**
 * OIDC configuration from Vite env.
 *
 * VITE_OIDC_ISSUER=http://localhost:8081/realms/itsm
 * VITE_OIDC_CLIENT_ID=itsm-spa
 * VITE_OIDC_REDIRECT_URI=http://localhost:5173/auth/callback
 * VITE_OIDC_ENABLED=true
 */

export interface OidcConfig {
  issuer: string;
  clientId: string;
  redirectUri: string;
  /** Standard Keycloak paths under the realm issuer. */
  authorizationEndpoint: string;
  tokenEndpoint: string;
  endSessionEndpoint: string;
  scopes: string;
}

function trimSlash(s: string): string {
  return s.replace(/\/+$/, '');
}

export function getDefaultRedirectUri(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return `${window.location.origin}/auth/callback`;
  }
  return 'http://localhost:5173/auth/callback';
}

function sameOriginOrDefault(configured: string | undefined): string {
  const fallback = getDefaultRedirectUri();
  if (!configured) return fallback;
  if (typeof window === 'undefined' || !window.location?.origin) return configured;
  try {
    return new URL(configured).origin === window.location.origin ? configured : fallback;
  } catch {
    return fallback;
  }
}

/**
 * OIDC UI + flow is on when VITE_OIDC_ENABLED=true and issuer + client id are set.
 * When false/unset, auth is optional (mock / dev-local still work).
 */
export function isOidcEnabled(): boolean {
  const flag = (import.meta.env.VITE_OIDC_ENABLED as string | undefined)?.trim();
  if (flag !== 'true' && flag !== '1') return false;
  const issuer = (import.meta.env.VITE_OIDC_ISSUER as string | undefined)?.trim();
  const clientId = (import.meta.env.VITE_OIDC_CLIENT_ID as string | undefined)?.trim();
  return Boolean(issuer && clientId);
}

export function getOidcConfig(): OidcConfig | null {
  if (!isOidcEnabled()) return null;

  const issuer = trimSlash(
    (import.meta.env.VITE_OIDC_ISSUER as string | undefined)?.trim() ?? '',
  );
  const clientId =
    (import.meta.env.VITE_OIDC_CLIENT_ID as string | undefined)?.trim() ?? '';
  if (!issuer || !clientId) return null;

  const redirectFromEnv = (
    import.meta.env.VITE_OIDC_REDIRECT_URI as string | undefined
  )?.trim();
  // PKCE state lives in sessionStorage, which is per-origin: a redirect URI pointing at a
  // different origin than the one being browsed can never complete the exchange. When the
  // build-time value disagrees with the current origin, the current origin wins.
  const redirectUri = sameOriginOrDefault(redirectFromEnv);

  return {
    issuer,
    clientId,
    redirectUri,
    authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
    endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
    scopes: 'openid profile email',
  };
}

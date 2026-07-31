/**
 * OIDC session persistence.
 * Full session (incl. refresh_token) lives in sessionStorage.
 * Access token is also mirrored to localStorage via setApiToken for apiRequest.
 */

import { setApiActorId, setApiToken } from '@/api/client';
import {
  decodeJwtPayload,
  displayNameFromPayload,
  initialsFromName,
  type JwtPayload,
} from './jwt';

const SESSION_KEY = 'vox-oidc-session';

export interface AuthUser {
  sub: string;
  name: string;
  email: string;
  username: string;
  initials: string;
  roles: string[];
}

export interface OidcSession {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  /** Unix ms when access token should be considered expired. */
  expiresAt: number;
  user: AuthUser;
}

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in?: number;
  token_type?: string;
  scope?: string;
}

export function userFromAccessToken(accessToken: string): AuthUser {
  const payload = decodeJwtPayload(accessToken);
  const name = displayNameFromPayload(payload) || 'User';
  return {
    sub: payload?.sub?.trim() || '',
    name,
    email: payload?.email?.trim() || '',
    username: payload?.preferred_username?.trim() || '',
    initials: initialsFromName(name),
    roles: payload?.realm_access?.roles ?? [],
  };
}

export function sessionFromTokenResponse(tokens: TokenResponse): OidcSession {
  const expiresIn = typeof tokens.expires_in === 'number' ? tokens.expires_in : 300;
  // Refresh 60s before expiry when possible
  const skew = Math.min(60, Math.floor(expiresIn / 2));
  return {
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    idToken: tokens.id_token,
    expiresAt: Date.now() + (expiresIn - skew) * 1000,
    user: userFromAccessToken(tokens.access_token),
  };
}

export function readSession(): OidcSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as OidcSession;
    if (!parsed?.accessToken || !parsed?.user?.sub) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function writeSession(session: OidcSession | null): void {
  try {
    if (!session) {
      sessionStorage.removeItem(SESSION_KEY);
    } else {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    }
  } catch {
    /* ignore */
  }

  if (session) {
    setApiToken(session.accessToken);
    setApiActorId(session.user.sub || null);
  } else {
    setApiToken(null);
    setApiActorId(null);
  }
}

export function clearSession(): void {
  writeSession(null);
}

export function accessTokenPayload(session: OidcSession | null): JwtPayload | null {
  if (!session?.accessToken) return null;
  return decodeJwtPayload(session.accessToken);
}

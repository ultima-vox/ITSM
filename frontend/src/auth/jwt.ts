/**
 * Minimal JWT payload decode (no signature verification — resource server validates).
 */

export interface JwtPayload {
  sub?: string;
  preferred_username?: string;
  name?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  exp?: number;
  iat?: number;
  realm_access?: { roles?: string[] };
  [key: string]: unknown;
}

function base64UrlToUtf8(input: string): string {
  const padded = input.replace(/-/g, '+').replace(/_/g, '/');
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
  const binary = atob(padded + pad);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2 || !parts[1]) return null;
    const json = base64UrlToUtf8(parts[1]);
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function displayNameFromPayload(payload: JwtPayload | null): string {
  if (!payload) return '';
  if (payload.name?.trim()) return payload.name.trim();
  const given = payload.given_name?.trim() ?? '';
  const family = payload.family_name?.trim() ?? '';
  const combined = `${given} ${family}`.trim();
  if (combined) return combined;
  if (payload.preferred_username?.trim()) return payload.preferred_username.trim();
  if (payload.email?.trim()) return payload.email.trim();
  if (payload.sub?.trim()) return payload.sub.trim();
  return '';
}

export function initialsFromName(name: string): string {
  const parts = name
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) {
    const w = parts[0]!;
    return w.slice(0, 2).toUpperCase();
  }
  return `${parts[0]![0] ?? ''}${parts[parts.length - 1]![0] ?? ''}`.toUpperCase();
}

export function isJwtExpired(payload: JwtPayload | null, skewSeconds = 30): boolean {
  if (!payload?.exp) return false;
  return Date.now() / 1000 >= payload.exp - skewSeconds;
}

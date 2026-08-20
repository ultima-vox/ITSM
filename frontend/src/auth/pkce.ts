/**
 * Pure TypeScript PKCE helpers (RFC 7636) + OAuth state.
 * No external crypto deps — uses Web Crypto API.
 */

const PKCE_VERIFIER_KEY = 'vox-oidc-pkce-verifier';
const PKCE_STATE_KEY = 'vox-oidc-pkce-state';
const PKCE_RETURN_KEY = 'vox-oidc-return-to';

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]!);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Cryptographically random URL-safe string (code_verifier / state). */
export function randomUrlSafeString(byteLength = 32): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return bytesToBase64Url(bytes);
}

/** S256 code_challenge = BASE64URL(SHA256(ASCII(code_verifier))) */
export async function createCodeChallenge(verifier: string): Promise<string> {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return bytesToBase64Url(new Uint8Array(digest));
}

export interface PkceLoginParams {
  codeVerifier: string;
  codeChallenge: string;
  state: string;
}

/** Create verifier + S256 challenge + state for an authorize redirect. */
export async function createPkceLoginParams(): Promise<PkceLoginParams> {
  const codeVerifier = randomUrlSafeString(32);
  const state = randomUrlSafeString(16);
  const codeChallenge = await createCodeChallenge(codeVerifier);
  return { codeVerifier, codeChallenge, state };
}

export function storePkceSession(
  codeVerifier: string,
  state: string,
  returnTo?: string,
): void {
  try {
    sessionStorage.setItem(PKCE_VERIFIER_KEY, codeVerifier);
    sessionStorage.setItem(PKCE_STATE_KEY, state);
    if (returnTo) {
      sessionStorage.setItem(PKCE_RETURN_KEY, returnTo);
    } else {
      sessionStorage.removeItem(PKCE_RETURN_KEY);
    }
  } catch {
    /* private mode */
  }
}

/** Read the pending authorization without consuming it (stale-callback detection). */
export function peekPkceSession(): {
  codeVerifier: string | null;
  state: string | null;
  returnTo: string | null;
} {
  try {
    return {
      codeVerifier: sessionStorage.getItem(PKCE_VERIFIER_KEY),
      state: sessionStorage.getItem(PKCE_STATE_KEY),
      returnTo: sessionStorage.getItem(PKCE_RETURN_KEY),
    };
  } catch {
    return { codeVerifier: null, state: null, returnTo: null };
  }
}

export function consumePkceSession(): {
  codeVerifier: string | null;
  state: string | null;
  returnTo: string | null;
} {
  try {
    const codeVerifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
    const state = sessionStorage.getItem(PKCE_STATE_KEY);
    const returnTo = sessionStorage.getItem(PKCE_RETURN_KEY);
    sessionStorage.removeItem(PKCE_VERIFIER_KEY);
    sessionStorage.removeItem(PKCE_STATE_KEY);
    sessionStorage.removeItem(PKCE_RETURN_KEY);
    return { codeVerifier, state, returnTo };
  } catch {
    return { codeVerifier: null, state: null, returnTo: null };
  }
}

export function clearPkceSession(): void {
  try {
    sessionStorage.removeItem(PKCE_VERIFIER_KEY);
    sessionStorage.removeItem(PKCE_STATE_KEY);
    sessionStorage.removeItem(PKCE_RETURN_KEY);
  } catch {
    /* ignore */
  }
}

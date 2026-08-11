import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getApiActorId, getApiToken } from '@/api/client';
import { clearSession, readSession, writeSession, type OidcSession } from './session';

function session(): OidcSession {
  return {
    accessToken: 'access-secret',
    refreshToken: 'refresh-secret',
    idToken: 'id-secret',
    expiresAt: Date.now() + 60_000,
    user: {
      sub: 'user-42', name: 'Test User', email: 'test@example.test', username: 'test',
      initials: 'TU', roles: ['REQUESTER'],
    },
  };
}

describe('OIDC session storage boundary', () => {
  const values = new Map<string, string>();

  beforeEach(() => {
    values.clear();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
    });
    vi.stubGlobal('sessionStorage', {
      getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn(),
    });
  });

  afterEach(() => {
    clearSession();
    vi.unstubAllGlobals();
  });

  it('keeps OAuth tokens and actor identity in memory only', () => {
    const active = session();
    writeSession(active);

    expect(readSession()).toBe(active);
    expect(getApiToken()).toBe('access-secret');
    expect(getApiActorId()).toBe('user-42');
    expect(values).toEqual(new Map());
    expect(sessionStorage.setItem).not.toHaveBeenCalled();
    expect(JSON.stringify([...values])).not.toContain('secret');
  });

  it('clears both session and API bearer on logout', () => {
    writeSession(session());
    clearSession();

    expect(readSession()).toBeNull();
    expect(getApiToken()).toBeNull();
    expect(getApiActorId()).toBe('dev-local');
    expect(values.has('vox-user-id')).toBe(false);
  });
});

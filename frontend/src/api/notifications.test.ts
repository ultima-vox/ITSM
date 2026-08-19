import { describe, expect, it, vi } from 'vitest';

import { listNotifications } from './notifications';

vi.mock('./client', async () => {
  const actual = await vi.importActual<typeof import('./client')>('./client');
  return { ...actual, isMockMode: () => false };
});

describe('live-mode notification snapshot', () => {
  it('returns a referentially stable value so useSyncExternalStore does not loop', () => {
    expect(listNotifications()).toBe(listNotifications());
    expect(listNotifications()).toEqual([]);
  });
});

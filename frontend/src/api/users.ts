import { apiRequest, isMockMode, delay } from './client';
import { people } from '@/mock/data';
import type { Person } from '@/types';

/** Backend user profile shape */
interface BackendUserProfile {
  subjectId: string;
  username: string;
  displayName: string;
  email: string;
  avatarUrl: string;
}

/** Local cache for resolved user profiles (session-scoped). */
const profileCache = new Map<string, Person>();

/**
 * Resolve a batch of subject IDs to Person objects.
 * Mock mode returns mock user data; live mode calls GET /users?ids=...
 */
export async function resolveUsers(subjectIds: string[]): Promise<Person[]> {
  const unique = [...new Set(subjectIds.filter(Boolean))];
  if (unique.length === 0) return [];

  if (isMockMode()) {
    await delay(30);
    return unique.map((sid) => {
      const found = Object.values(people).find((p) => p.id === sid);
      if (found) return found;
      return { id: sid, name: sid, initials: sid.slice(0, 2).toUpperCase() };
    });
  }

  const uncached = unique.filter((id) => !profileCache.has(id));
  if (uncached.length > 0) {
    const idsParam = uncached.join(',');
    const map = await apiRequest<Record<string, BackendUserProfile>>(
      `/users?ids=${encodeURIComponent(idsParam)}`,
    );
    for (const [sid, profile] of Object.entries(map)) {
      const initials = profile.displayName
        ? profile.displayName
            .split(/\s+/)
            .map((w) => w[0])
            .join('')
            .toUpperCase()
            .slice(0, 2)
        : sid.slice(0, 2).toUpperCase();
      profileCache.set(sid, {
        id: sid,
        name: profile.displayName || profile.username || sid,
        initials,
      });
    }
    // Cache fallbacks for IDs not returned by the server
    for (const sid of uncached) {
      if (!profileCache.has(sid)) {
        profileCache.set(sid, {
          id: sid,
          name: sid,
          initials: sid.slice(0, 2).toUpperCase(),
        });
      }
    }
  }
  return unique.map((sid) => profileCache.get(sid)!);
}

/**
 * Synchronous fallback for places that can't await.
 * Returns cached profile if available, otherwise a stub.
 */
export function resolveUserSync(subjectId: string): Person {
  const cached = profileCache.get(subjectId);
  if (cached) return cached;
  return { id: subjectId, name: subjectId, initials: subjectId.slice(0, 2).toUpperCase() };
}

/**
 * Pre-warm the cache with the given subject IDs.
 * Call this early in app lifecycle or when rendering lists.
 */
export async function preloadUserProfiles(subjectIds: string[]): Promise<void> {
  await resolveUsers(subjectIds);
}

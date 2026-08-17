import { useMemo } from 'react';
import { useAuth } from '@/auth';
import { currentUser } from '@/mock/data';
import { isMockMode, getApiActorId } from '@/api/client';
import type { UserProfile } from '@/types';

/**
 * Returns the current user profile.
 * - Mock mode: hardcoded mock user (demo data context).
 * - Live mode: derives profile from OIDC session or falls back to API actor ID.
 */
export function useCurrentUser(): UserProfile {
  const { user } = useAuth();

  return useMemo(() => {
    if (isMockMode()) return currentUser;

    if (user) {
      return {
        id: user.sub,
        name: user.name || user.username || user.sub,
        email: user.email || '',
        role: user.roles?.[0] ?? '',
        team: '',
        teamId: '',
        initials: user.initials || computeInitials(user.name || user.username || user.sub),
        timezone: 'UTC',
      };
    }

    const actorId = getApiActorId();
    return {
      id: actorId,
      name: actorId,
      email: '',
      role: '',
      team: '',
      teamId: '',
      initials: computeInitials(actorId),
      timezone: 'UTC',
    };
  }, [user]);
}

function computeInitials(name: string): string {
  const parts = name.trim().split(/[\s._-]+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return name.slice(0, 2).toUpperCase();
}

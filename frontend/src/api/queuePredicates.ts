import type { WorkItem } from '@/types';

/** Queue predicates — shared by mock and live modes */
export function isUnassigned(w: WorkItem): boolean {
  return !w.assignee;
}

export function isMyGroup(w: WorkItem, teamId?: string): boolean {
  if (!teamId) return false;
  return w.teamId === teamId;
}

export function isEscalated(w: WorkItem): boolean {
  return (
    w.escalated === true ||
    w.priority === 'critical' ||
    (w.tags?.includes('escalated') ?? false)
  );
}

export function isBreached(w: WorkItem): boolean {
  return w.slaState === 'breached';
}

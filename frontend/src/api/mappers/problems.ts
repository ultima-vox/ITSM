import type { Priority, Problem, WorkItemStatus } from '@/types';

export interface BackendProblemSummary {
  id: string;
  number: string;
  title: string;
  status: string;
  rootCause?: string | null;
  workaround?: string | null;
  createdAt?: string;
  updatedAt?: string;
  linkedWorkItems?: string[];
}

function mapProblemStatus(status: string): WorkItemStatus {
  switch (status.toUpperCase()) {
    case 'NEW':
      return 'new';
    case 'UNDER_INVESTIGATION':
    case 'ROOT_CAUSE_IDENTIFIED':
      return 'in_progress';
    case 'KNOWN_ERROR':
      return 'waiting';
    case 'RESOLVED':
      return 'resolved';
    case 'CLOSED':
      return 'closed';
    default:
      return 'new';
  }
}

export function mapProblem(dto: BackendProblemSummary): Problem {
  const knownError = (dto.status ?? '').toUpperCase() === 'KNOWN_ERROR';
  const priority: Priority = knownError ? 'high' : 'medium';
  const linked = dto.linkedWorkItems?.length ?? 0;

  return {
    id: String(dto.id),
    number: dto.number,
    title: dto.title,
    status: mapProblemStatus(dto.status),
    priority,
    knownError,
    relatedIncidents: linked,
    assignee: null,
    updatedAt: dto.updatedAt ?? dto.createdAt ?? new Date().toISOString(),
    description: dto.title,
    rootCause: dto.rootCause ?? undefined,
    workaround: dto.workaround ?? undefined,
  };
}

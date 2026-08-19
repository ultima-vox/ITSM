import type { CabVote, Change, Priority } from '@/types';

export interface BackendChange {
  id: string;
  version?: number;
  number: string;
  type: string;
  risk: string;
  status: string;
  title: string;
  plannedStart?: string | null;
  plannedEnd?: string | null;
  implementationPlan?: string | null;
  rollbackPlan?: string | null;
  testPlan?: string | null;
  businessJustification?: string | null;
  cabNotes?: string | null;
  cabRiskLevel?: string | null;
}

export interface BackendCabVote {
  id?: string;
  changeId?: string;
  approverId: string;
  decision: string;
  decidedAt?: string | null;
  comment?: string | null;
}

function mapChangeType(type: string): Change['type'] {
  switch (type.toUpperCase()) {
    case 'STANDARD':
      return 'standard';
    case 'EMERGENCY':
      return 'emergency';
    default:
      return 'normal';
  }
}

function mapChangeStatus(status: string): Change['status'] {
  switch (status.toUpperCase()) {
    case 'DRAFT':
      return 'draft';
    case 'SUBMITTED':
      return 'submitted';
    case 'CAB_REVIEW':
      return 'cab_review';
    case 'APPROVED':
      return 'approved';
    case 'SCHEDULED':
      return 'scheduled';
    case 'IMPLEMENTING':
    case 'IN_PROGRESS':
      return 'implementing';
    case 'REVIEW':
      return 'review';
    case 'CLOSED':
    case 'COMPLETED':
      return 'completed';
    case 'REJECTED':
    case 'CANCELLED':
    case 'CANCELED':
      return 'cancelled';
    default:
      return 'draft';
  }
}

function mapRisk(risk: string): Priority {
  switch (risk.toUpperCase()) {
    case 'CRITICAL':
      return 'critical';
    case 'HIGH':
      return 'high';
    case 'LOW':
      return 'low';
    default:
      return 'medium';
  }
}

export function mapCabVote(dto: BackendCabVote): CabVote {
  const decision = (dto.decision ?? '').toUpperCase();
  return {
    memberId: dto.approverId,
    memberName: dto.approverId,
    initials: initialsFrom(dto.approverId),
    decision:
      decision.startsWith('APPROV')
        ? 'approve'
        : decision.startsWith('REJECT')
          ? 'reject'
          : undefined,
    at: dto.decidedAt ?? undefined,
  };
}

function initialsFrom(id: string): string {
  const compact = id.replace(/[^a-zA-Z0-9]/g, '');
  return (compact.slice(0, 2) || '?').toUpperCase();
}

export function mapChange(dto: BackendChange): Change {
  return {
    id: String(dto.id),
    version: dto.version ?? 0,
    number: dto.number,
    title: dto.title,
    type: mapChangeType(dto.type),
    status: mapChangeStatus(dto.status),
    risk: mapRisk(dto.cabRiskLevel ?? dto.risk),
    plannedStart: dto.plannedStart ?? '',
    plannedEnd: dto.plannedEnd ?? '',
    assignee: null,
    updatedAt: '',
    description: dto.businessJustification ?? undefined,
    implementationPlan: dto.implementationPlan ?? undefined,
    backoutPlan: dto.rollbackPlan ?? undefined,
    cabNotes: dto.cabNotes ?? undefined,
  };
}

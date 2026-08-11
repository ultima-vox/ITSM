import type { Change, Priority } from '@/types';

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
  businessJustification?: string | null;
  cabNotes?: string | null;
  cabRiskLevel?: string | null;
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
    case 'SUBMITTED':
    case 'APPROVED':
      return 'draft';
    case 'CAB_REVIEW':
      return 'cab_review';
    case 'SCHEDULED':
      return 'scheduled';
    case 'IMPLEMENTING':
    case 'REVIEW':
      return 'in_progress';
    case 'CLOSED':
      return 'completed';
    case 'REJECTED':
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

export function mapChange(dto: BackendChange): Change {
  return {
    id: String(dto.id),
    version: dto.version ?? 0,
    number: dto.number,
    title: dto.title,
    type: mapChangeType(dto.type),
    status: mapChangeStatus(dto.status),
    risk: mapRisk(dto.risk),
    plannedStart: dto.plannedStart ?? new Date().toISOString(),
    plannedEnd: dto.plannedEnd ?? new Date().toISOString(),
    assignee: null,
    updatedAt: dto.plannedStart ?? new Date().toISOString(),
    description: dto.businessJustification ?? undefined,
    implementationPlan: dto.implementationPlan ?? undefined,
    backoutPlan: dto.rollbackPlan ?? undefined,
  };
}

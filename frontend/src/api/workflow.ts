/**
 * Workflow definitions — live list from backend; mock for local edits.
 */
import { apiRequest, delay, isMockMode } from './client';
import {
  listWorkflowDefinitions as mockList,
  setWorkflowActiveVersion as mockSetActive,
  subscribeWorkflowDefinitions as mockSubscribe,
} from '@/mock/workflow';
import type { WorkflowDefinition } from '@/types';

interface BackendTransition {
  key: string;
  from: string;
  to: string;
  requiredPermissions?: string[];
  requiredFields?: string[];
  approval?: { mode: 'ANY' | 'ALL' | 'QUORUM'; voterRoles: string[]; quorum?: number };
  timer?: { delaySeconds: number; maxAttempts: number };
}

interface BackendDefinition {
  id: string;
  objectKey: string;
  version: number;
  active: boolean;
  initialState: string;
  states: string[];
  transitions: BackendTransition[];
}

export interface WorkflowInstanceView {
  id: string;
  objectType: string;
  objectId: string;
  state: string;
  definitionVersion: number;
  version: number;
  updatedAt: string;
}

export interface WorkflowApprovalView {
  id: string;
  transitionKey: string;
  definitionVersion: number;
  sourceInstanceVersion: number;
  attempt: number;
  mode: 'ANY' | 'ALL' | 'QUORUM';
  quorum?: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CONSUMED';
  requestedBy: string;
  createdAt: string;
  completedAt?: string;
  consumedAt?: string;
  votes: Array<{
    voterId: string;
    voterRole: string;
    decision?: 'APPROVED' | 'REJECTED';
    comment?: string;
    decidedAt?: string;
  }>;
}

export interface WorkflowTimerView {
  id: string;
  transitionKey: string;
  definitionVersion: number;
  sourceInstanceVersion: number;
  dueAt: string;
  status: 'PENDING' | 'PROCESSING' | 'RETRY' | 'COMPLETED' | 'CANCELLED' | 'DEAD';
  attempts: number;
  maxAttempts: number;
  lastError?: string;
}

function mapDef(dto: BackendDefinition): WorkflowDefinition {
  return {
    id: dto.id,
    objectKey: dto.objectKey,
    version: dto.version,
    active: dto.active,
    initialState: dto.initialState,
    states: dto.states ?? [],
    transitions: (dto.transitions ?? []).map((t) => ({
      key: t.key,
      from: t.from,
      to: t.to,
      requiredPermissions: t.requiredPermissions ?? [],
      requiredFields: t.requiredFields ?? [],
      approval: t.approval,
      timer: t.timer,
    })),
    name: dto.objectKey,
  };
}

export async function fetchWorkflowDefinitions(): Promise<WorkflowDefinition[]> {
  if (isMockMode()) {
    await delay(140);
    return mockList();
  }
  const list = await apiRequest<BackendDefinition[]>('/workflow/definitions');
  return (list ?? []).map(mapDef);
}

export async function setWorkflowActiveVersion(
  id: string,
  active: boolean,
): Promise<WorkflowDefinition | null> {
  if (isMockMode()) {
    await delay(80);
    return mockSetActive(id, active);
  }
  const changed = await apiRequest<BackendDefinition>(
    `/workflow/definitions/${encodeURIComponent(id)}`,
    { method: 'PATCH', body: { active } },
  );
  return mapDef(changed);
}

export async function fetchWorkflowInstance(
  objectType: string,
  objectId: string,
): Promise<WorkflowInstanceView> {
  return apiRequest<WorkflowInstanceView>(
    `/workflow/instances/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}`,
  );
}

export async function migrateWorkflowInstance(
  instance: WorkflowInstanceView,
  targetDefinitionVersion: number,
): Promise<WorkflowInstanceView> {
  return apiRequest<WorkflowInstanceView>(
    `/workflow/instances/${encodeURIComponent(instance.objectType)}/${encodeURIComponent(instance.objectId)}/migrations`,
    { method: 'POST', body: { targetDefinitionVersion, expectedVersion: instance.version } },
  );
}

export async function fetchWorkflowApprovals(
  objectType: string, objectId: string,
): Promise<WorkflowApprovalView[]> {
  return apiRequest<WorkflowApprovalView[]>(
    `/workflow/instances/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/approvals`);
}

export async function fetchWorkflowTimers(
  objectType: string, objectId: string,
): Promise<WorkflowTimerView[]> {
  return apiRequest<WorkflowTimerView[]>(
    `/workflow/instances/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/timers`);
}

export async function requestWorkflowApproval(
  objectType: string, objectId: string, transitionKey: string,
): Promise<WorkflowApprovalView> {
  return apiRequest<WorkflowApprovalView>(
    `/workflow/instances/${encodeURIComponent(objectType)}/${encodeURIComponent(objectId)}/approvals`,
    { method: 'POST', body: { transitionKey } });
}

export async function voteWorkflowApproval(
  id: string, decision: 'APPROVED' | 'REJECTED', comment?: string,
): Promise<WorkflowApprovalView> {
  return apiRequest<WorkflowApprovalView>(`/workflow/approvals/${encodeURIComponent(id)}/votes`,
    { method: 'POST', body: { decision, comment } });
}

export function workflowDefinitionsWritable(): boolean {
  return true;
}

export function subscribeWorkflowDefinitions(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}

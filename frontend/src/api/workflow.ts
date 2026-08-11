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

export function workflowDefinitionsWritable(): boolean {
  return true;
}

export function subscribeWorkflowDefinitions(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}

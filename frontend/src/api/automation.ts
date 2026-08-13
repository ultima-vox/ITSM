/**
 * Automation rules — live list from backend; mock for local demo edits.
 */
import { apiRequest, delay, isMockMode } from './client';
import {
  listAutomationRules as mockList,
  listAutomationExecutions as mockExecutions,
  setAutomationRuleEnabled as mockSetEnabled,
  subscribeAutomationRules as mockSubscribe,
} from '@/mock/automation';
import type { AutomationExecution, AutomationRule } from '@/types';

interface BackendCondition {
  field: string;
  operator: string;
  value: string;
}

interface BackendAction {
  type: string;
  parameters?: Record<string, unknown>;
}

interface BackendRule {
  id: string;
  key: string;
  name: string;
  version: number;
  enabled: boolean;
  eventType: string;
  conditions: BackendCondition[];
  actions: BackendAction[];
}

function mapRule(dto: BackendRule): AutomationRule {
  return {
    id: dto.id,
    ruleKey: dto.key,
    name: dto.name ?? dto.key,
    version: dto.version,
    enabled: dto.enabled,
    trigger: { eventType: dto.eventType },
    conditions: (dto.conditions ?? []).map((c) => ({
      field: c.field,
      operator: (c.operator as AutomationRule['conditions'][number]['operator']) ?? 'EQUALS',
      value: c.value,
    })),
    actions: (dto.actions ?? []).map((a) => ({
      type: a.type,
      parameters: a.parameters ?? {},
    })),
  };
}

export async function saveAutomationRule(
  rule: Omit<AutomationRule, 'id' | 'description'>,
  id?: string,
): Promise<AutomationRule> {
  const changed = await apiRequest<BackendRule>(
    id ? `/automation/rules/${encodeURIComponent(id)}?expectedVersion=${rule.version}` : '/automation/rules',
    { method: id ? 'PUT' : 'POST', body: rule },
  );
  return mapRule(changed);
}

export async function fetchAutomationRules(): Promise<AutomationRule[]> {
  if (isMockMode()) {
    await delay(140);
    return mockList();
  }
  const list = await apiRequest<BackendRule[]>('/automation/rules');
  return (list ?? []).map(mapRule);
}

export async function setAutomationRuleEnabled(
  id: string,
  enabled: boolean,
): Promise<AutomationRule | null> {
  if (isMockMode()) {
    await delay(60);
    return mockSetEnabled(id, enabled);
  }
  const changed = await apiRequest<BackendRule>(
    `/automation/rules/${encodeURIComponent(id)}`,
    { method: 'PATCH', body: { enabled } },
  );
  return mapRule(changed);
}

export function automationRulesWritable(): boolean {
  return true;
}

interface BackendExecution {
  id: string;
  ruleKey: string;
  eventId: string;
  actionType: string;
  status: AutomationExecution['status'];
  details?: Record<string, unknown>;
  createdAt?: string;
}

function mapExecution(dto: BackendExecution): AutomationExecution {
  return {
    id: dto.id,
    ruleKey: dto.ruleKey,
    eventId: dto.eventId,
    actionType: dto.actionType,
    status: dto.status,
    details: dto.details ?? {},
    createdAt: dto.createdAt ?? new Date().toISOString(),
  };
}

export async function fetchAutomationExecutions(options?: {
  ruleKey?: string;
  status?: AutomationExecution['status'];
  limit?: number;
  signal?: AbortSignal;
}): Promise<AutomationExecution[]> {
  const limit = options?.limit ?? 100;

  if (isMockMode()) {
    await delay(140);
    let list = mockExecutions();
    if (options?.ruleKey) list = list.filter((e) => e.ruleKey === options.ruleKey);
    if (options?.status) list = list.filter((e) => e.status === options.status);
    return list.slice(0, limit);
  }

  const qs = new URLSearchParams();
  if (options?.ruleKey) qs.set('ruleKey', options.ruleKey);
  if (options?.status) qs.set('status', options.status);
  qs.set('limit', String(limit));
  const hits = await apiRequest<BackendExecution[]>(`/automation/executions?${qs}`, {
    signal: options?.signal,
  });
  return (hits ?? []).map(mapExecution);
}

export function subscribeAutomationRules(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}

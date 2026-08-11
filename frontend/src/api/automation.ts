/**
 * Automation rules — live list from backend; mock for local demo edits.
 */
import { apiRequest, delay, isMockMode } from './client';
import {
  listAutomationRules as mockList,
  setAutomationRuleEnabled as mockSetEnabled,
  subscribeAutomationRules as mockSubscribe,
} from '@/mock/automation';
import type { AutomationRule } from '@/types';

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

export function subscribeAutomationRules(listener: () => void): () => void {
  if (isMockMode()) return mockSubscribe(listener);
  return () => undefined;
}

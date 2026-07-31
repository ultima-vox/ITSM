/**
 * Mock automation rules + session-scoped enable/disable store.
 * Aligned with backend `AutomationRule` (WHEN event IF conditions THEN actions).
 */
import type { AutomationRule } from '@/types';

type Listener = () => void;

const listeners = new Set<Listener>();

/** Seed: 3 sample rules covering notify / index / escalate (mock). */
const SEED_RULES: AutomationRule[] = [
  {
    id: 'ar-001',
    ruleKey: 'notify-on-critical-incident',
    name: 'Notify on critical incident',
    enabled: true,
    description:
      'When a critical incident is created, send an in-app notification to on-call.',
    trigger: { eventType: 'incident.created' },
    conditions: [
      { field: 'priority', operator: 'EQUALS', value: 'CRITICAL' },
    ],
    actions: [
      {
        type: 'notify',
        parameters: {
          templateKey: 'incident.created.critical',
          channel: 'IN_APP',
          recipientSubject: 'oncall.primary',
        },
      },
    ],
  },
  {
    id: 'ar-002',
    ruleKey: 'index-on-work-item-resolved',
    name: 'Index on resolve',
    enabled: true,
    description:
      'When a work item is resolved, re-index the search document for knowledge lookup.',
    trigger: { eventType: 'work-item.resolved' },
    conditions: [
      { field: 'type', operator: 'IN', value: 'INCIDENT,SERVICE_REQUEST' },
    ],
    actions: [
      {
        type: 'index',
        parameters: {
          title: 'Resolved work item',
          body: 'resolution_notes',
        },
      },
    ],
  },
  {
    id: 'ar-003',
    ruleKey: 'escalate-on-sla-breach',
    name: 'Escalate on SLA breach',
    enabled: false,
    description:
      'When an SLA breach event fires, log escalation and notify the service owner (mock escalate).',
    trigger: { eventType: 'sla.breached' },
    conditions: [
      { field: 'metric', operator: 'EQUALS', value: 'resolution' },
    ],
    actions: [
      {
        type: 'log',
        parameters: {
          level: 'WARN',
          message: 'SLA breach — escalate to manager',
        },
      },
      {
        type: 'notify',
        parameters: {
          templateKey: 'sla.breached.escalate',
          channel: 'IN_APP',
          recipientSubject: 'service.owner',
        },
      },
    ],
  },
];

/** Session-mutable copy of rules (enable flags flip without reload). */
let rules: AutomationRule[] = SEED_RULES.map(cloneRule);

function cloneRule(r: AutomationRule): AutomationRule {
  return {
    ...r,
    trigger: { ...r.trigger },
    conditions: r.conditions.map((c) => ({ ...c })),
    actions: r.actions.map((a) => ({
      type: a.type,
      parameters: { ...a.parameters },
    })),
  };
}

function notify() {
  listeners.forEach((fn) => fn());
}

export function subscribeAutomationRules(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function listAutomationRules(): AutomationRule[] {
  return rules.map(cloneRule);
}

export function getAutomationRule(id: string): AutomationRule | null {
  const found = rules.find((r) => r.id === id || r.ruleKey === id);
  return found ? cloneRule(found) : null;
}

/** Toggle or set enabled flag for a rule (session store). */
export function setAutomationRuleEnabled(
  id: string,
  enabled: boolean,
): AutomationRule | null {
  const idx = rules.findIndex((r) => r.id === id || r.ruleKey === id);
  if (idx < 0) return null;
  rules[idx] = { ...rules[idx], enabled };
  notify();
  return cloneRule(rules[idx]);
}

export function toggleAutomationRuleEnabled(id: string): AutomationRule | null {
  const current = rules.find((r) => r.id === id || r.ruleKey === id);
  if (!current) return null;
  return setAutomationRuleEnabled(id, !current.enabled);
}

/** Restore seed enabled flags (used by demo reset if wired). */
export function resetAutomationRules(): void {
  rules = SEED_RULES.map(cloneRule);
  notify();
}

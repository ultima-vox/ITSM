/**
 * Mock workflow definitions + session-scoped active-version store.
 * Shaped like backend `WorkflowDefinition` (objectKey, version, states, transitions).
 */
import type { WorkflowDefinition, WorkflowTransition } from '@/types';

type Listener = () => void;

const listeners = new Set<Listener>();

function t(
  key: string,
  from: string,
  to: string,
  perms: string[] = [],
  fields: string[] = [],
): WorkflowTransition {
  return {
    key,
    from,
    to,
    requiredPermissions: perms,
    requiredFields: fields,
  };
}

/**
 * Seed: work-item (from V10 SQL), change, problem — Naumen-class lifecycle depth.
 * Two versions of work-item so active-version toggle is meaningful.
 */
const SEED: WorkflowDefinition[] = [
  {
    id: 'wf-work-item-v1',
    objectKey: 'work-item',
    version: 1,
    active: true,
    initialState: 'NEW',
    name: 'Work item lifecycle',
    description:
      'Incident / service request states with hold, resolve, reopen and cancel paths.',
    states: ['NEW', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED', 'CANCELLED'],
    transitions: [
      t('start', 'NEW', 'IN_PROGRESS', ['work-item.transition'], ['assignee_id']),
      t('hold', 'IN_PROGRESS', 'PENDING', ['work-item.transition']),
      t('resume', 'PENDING', 'IN_PROGRESS', ['work-item.transition']),
      t('resolve', 'IN_PROGRESS', 'RESOLVED', ['work-item.transition']),
      t('reopen', 'RESOLVED', 'IN_PROGRESS', ['work-item.transition']),
      t('close', 'RESOLVED', 'CLOSED', ['work-item.close']),
      t('cancel-new', 'NEW', 'CANCELLED', ['work-item.update']),
      t('cancel-wip', 'IN_PROGRESS', 'CANCELLED', ['work-item.update']),
      t('cancel-hold', 'PENDING', 'CANCELLED', ['work-item.update']),
    ],
  },
  {
    id: 'wf-work-item-v2',
    objectKey: 'work-item',
    version: 2,
    active: false,
    initialState: 'NEW',
    name: 'Work item lifecycle (draft v2)',
    description:
      'Draft revision — adds explicit reassignment gate before start (inactive until activated).',
    states: ['NEW', 'TRIAGED', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED', 'CANCELLED'],
    transitions: [
      t('triage', 'NEW', 'TRIAGED', ['work-item.transition'], ['priority']),
      t('start', 'TRIAGED', 'IN_PROGRESS', ['work-item.transition'], ['assignee_id']),
      t('hold', 'IN_PROGRESS', 'PENDING', ['work-item.transition']),
      t('resume', 'PENDING', 'IN_PROGRESS', ['work-item.transition']),
      t('resolve', 'IN_PROGRESS', 'RESOLVED', ['work-item.transition'], ['resolution_notes']),
      t('reopen', 'RESOLVED', 'IN_PROGRESS', ['work-item.transition']),
      t('close', 'RESOLVED', 'CLOSED', ['work-item.close']),
      t('cancel-new', 'NEW', 'CANCELLED', ['work-item.update']),
      t('cancel-triaged', 'TRIAGED', 'CANCELLED', ['work-item.update']),
      t('cancel-wip', 'IN_PROGRESS', 'CANCELLED', ['work-item.update']),
    ],
  },
  {
    id: 'wf-change-v1',
    objectKey: 'change',
    version: 1,
    active: true,
    initialState: 'DRAFT',
    name: 'Change lifecycle',
    description:
      'Standard / normal / emergency change path including CAB review and completion.',
    states: ['DRAFT', 'SCHEDULED', 'CAB_REVIEW', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'],
    transitions: [
      t('submit-cab', 'DRAFT', 'CAB_REVIEW', ['change.manage'], ['implementation_plan']),
      t('schedule-std', 'DRAFT', 'SCHEDULED', ['change.manage'], ['window_start', 'window_end']),
      t('approve-cab', 'CAB_REVIEW', 'SCHEDULED', ['change.approve']),
      t('reject-cab', 'CAB_REVIEW', 'DRAFT', ['change.approve']),
      t('start', 'SCHEDULED', 'IN_PROGRESS', ['change.write'], ['assignee_id']),
      t('complete', 'IN_PROGRESS', 'COMPLETED', ['change.write']),
      t('cancel-draft', 'DRAFT', 'CANCELLED', ['change.manage']),
      t('cancel-cab', 'CAB_REVIEW', 'CANCELLED', ['change.manage']),
      t('cancel-sched', 'SCHEDULED', 'CANCELLED', ['change.manage']),
    ],
  },
  {
    id: 'wf-problem-v1',
    objectKey: 'problem',
    version: 1,
    active: true,
    initialState: 'NEW',
    name: 'Problem lifecycle',
    description:
      'Problem investigation with known-error fields required before resolve.',
    states: ['NEW', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED'],
    transitions: [
      t('start', 'NEW', 'IN_PROGRESS', ['problem.write'], ['assignee_id']),
      t('hold', 'IN_PROGRESS', 'PENDING', ['problem.write']),
      t('resume', 'PENDING', 'IN_PROGRESS', ['problem.write']),
      t(
        'resolve',
        'IN_PROGRESS',
        'RESOLVED',
        ['problem.write'],
        ['root_cause'],
      ),
      t('reopen', 'RESOLVED', 'IN_PROGRESS', ['problem.write']),
      t('close', 'RESOLVED', 'CLOSED', ['problem.write']),
    ],
  },
];

let definitions: WorkflowDefinition[] = SEED.map(cloneDef);

function cloneDef(d: WorkflowDefinition): WorkflowDefinition {
  return {
    ...d,
    states: [...d.states],
    transitions: d.transitions.map((tr) => ({
      ...tr,
      requiredPermissions: [...tr.requiredPermissions],
      requiredFields: [...tr.requiredFields],
    })),
  };
}

function notify() {
  listeners.forEach((fn) => fn());
}

export function subscribeWorkflowDefinitions(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function listWorkflowDefinitions(): WorkflowDefinition[] {
  return definitions.map(cloneDef);
}

export function getWorkflowDefinition(id: string): WorkflowDefinition | null {
  const found = definitions.find((d) => d.id === id);
  return found ? cloneDef(found) : null;
}

/**
 * Mark a definition active for its objectKey; deactivate sibling versions (session).
 */
export function setWorkflowActiveVersion(id: string, active: boolean): WorkflowDefinition | null {
  const target = definitions.find((d) => d.id === id);
  if (!target) return null;

  if (active) {
    definitions = definitions.map((d) => {
      if (d.objectKey !== target.objectKey) return d;
      return { ...d, active: d.id === id };
    });
  } else {
    // Do not leave objectKey without an active version if another exists
    const siblings = definitions.filter((d) => d.objectKey === target.objectKey);
    if (siblings.length === 1) {
      // Keep single version active
      definitions = definitions.map((d) =>
        d.id === id ? { ...d, active: true } : d,
      );
    } else {
      definitions = definitions.map((d) => {
        if (d.id === id) return { ...d, active: false };
        return d;
      });
      const stillActive = definitions.some(
        (d) => d.objectKey === target.objectKey && d.active,
      );
      if (!stillActive) {
        // Prefer highest version as active fallback
        const best = [...definitions]
          .filter((d) => d.objectKey === target.objectKey)
          .sort((a, b) => b.version - a.version)[0];
        if (best) {
          definitions = definitions.map((d) =>
            d.id === best.id ? { ...d, active: true } : d,
          );
        }
      }
    }
  }

  notify();
  return getWorkflowDefinition(id);
}

export function resetWorkflowDefinitions(): void {
  definitions = SEED.map(cloneDef);
  notify();
}

/**
 * Mock release store, shaped like backend `Release` and `ReleaseContentEntry`.
 * Dev only — the live API never reads this module.
 */
import type {
  CreateReleasePayload,
  Release,
  ReleaseContentEntry,
  ReleaseGoDecision,
  ReleaseStatus,
} from '@/types';

const ALLOWED: Record<ReleaseStatus, ReleaseStatus[]> = {
  PLANNING: ['BUILD', 'CANCELLED'],
  BUILD: ['TESTING', 'PLANNING', 'CANCELLED'],
  TESTING: ['GO_NO_GO', 'BUILD', 'CANCELLED'],
  GO_NO_GO: ['DEPLOYING', 'BUILD', 'CANCELLED'],
  DEPLOYING: ['DEPLOYED', 'ROLLED_BACK'],
  DEPLOYED: ['CLOSED', 'ROLLED_BACK'],
  ROLLED_BACK: ['PLANNING', 'CLOSED'],
  CLOSED: [],
  CANCELLED: [],
};

const DEPLOYABLE_CHANGE_STATES = new Set([
  'APPROVED',
  'SCHEDULED',
  'IMPLEMENTING',
  'REVIEW',
  'CLOSED',
]);

let sequence = 1002;

const releases: Release[] = [
  {
    id: 'rel-1000',
    number: 'REL-001000',
    name: 'Payments 4.2',
    type: 'MINOR',
    status: 'GO_NO_GO',
    description: 'Quarterly payment gateway release.',
    deploymentPlan: 'Blue-green switch behind the edge, 10% canary for 30 minutes.',
    rollbackPlan: 'Return traffic to the blue stack, keep the database migration forward-compatible.',
    testSummary: 'Regression suite green, load test within thresholds.',
    goDecision: null,
    goDecisionNotes: null,
    goDecidedBy: null,
    goDecidedAt: null,
    releaseManager: 'carol',
    plannedStart: '2026-08-28T20:00:00Z',
    plannedEnd: '2026-08-28T23:00:00Z',
    actualStart: null,
    actualEnd: null,
    version: 3,
  },
  {
    id: 'rel-1001',
    number: 'REL-001001',
    name: 'Workplace hardware refresh',
    type: 'PATCH',
    status: 'DEPLOYED',
    description: 'Agent image rollout for the support desk laptops.',
    deploymentPlan: 'Staged rollout, one office per evening.',
    rollbackPlan: 'Restore the previous image from the deployment share.',
    testSummary: 'Pilot group signed off.',
    goDecision: 'GO',
    goDecisionNotes: 'Pilot feedback accepted.',
    goDecidedBy: 'carol',
    goDecidedAt: '2026-08-14T09:00:00Z',
    releaseManager: 'carol',
    plannedStart: '2026-08-15T18:00:00Z',
    plannedEnd: '2026-08-15T22:00:00Z',
    actualStart: '2026-08-15T18:05:00Z',
    actualEnd: '2026-08-15T21:12:00Z',
    version: 8,
  },
];

const content: Record<string, ReleaseContentEntry[]> = {
  'rel-1000': [
    {
      changeId: 'chg-2001',
      number: 'CHG-002001',
      title: 'Upgrade the payment gateway',
      type: 'NORMAL',
      status: 'APPROVED',
      plannedStart: '2026-08-28T20:00:00Z',
      plannedEnd: '2026-08-28T21:00:00Z',
      deployable: true,
    },
    {
      changeId: 'chg-2002',
      number: 'CHG-002002',
      title: 'Rotate the acquirer certificates',
      type: 'STANDARD',
      status: 'SUBMITTED',
      plannedStart: null,
      plannedEnd: null,
      deployable: false,
    },
  ],
  'rel-1001': [],
};

function clone(release: Release): Release {
  return { ...release };
}

function mustFind(id: string): Release {
  const found = releases.find((release) => release.id === id);
  if (!found) throw new Error(`Release not found: ${id}`);
  return found;
}

function frozen(status: ReleaseStatus): boolean {
  return ['DEPLOYING', 'DEPLOYED', 'ROLLED_BACK', 'CLOSED', 'CANCELLED'].includes(status);
}

export function listReleases(): Release[] {
  return releases.map(clone);
}

export function getRelease(id: string): Release {
  return clone(mustFind(id));
}

export function addRelease(payload: CreateReleasePayload): Release {
  sequence += 1;
  const created: Release = {
    id: `rel-${sequence}`,
    number: `REL-${String(sequence).padStart(6, '0')}`,
    name: payload.name,
    type: payload.type,
    status: 'PLANNING',
    description: payload.description ?? null,
    deploymentPlan: payload.deploymentPlan ?? null,
    rollbackPlan: payload.rollbackPlan ?? null,
    testSummary: null,
    goDecision: null,
    goDecisionNotes: null,
    goDecidedBy: null,
    goDecidedAt: null,
    releaseManager: payload.releaseManager ?? 'dev-local',
    plannedStart: payload.plannedStart ?? null,
    plannedEnd: payload.plannedEnd ?? null,
    actualStart: null,
    actualEnd: null,
    version: 0,
  };
  releases.unshift(created);
  content[created.id] = [];
  return clone(created);
}

export function updateRelease(id: string, patch: Partial<Release>): Release {
  const release = mustFind(id);
  if (frozen(release.status)) {
    throw new Error('A release that reached deployment cannot be edited');
  }
  Object.assign(release, patch, { version: release.version + 1 });
  return clone(release);
}

export function transitionRelease(id: string, target: ReleaseStatus): Release {
  const release = mustFind(id);
  if (!ALLOWED[release.status].includes(target)) {
    throw new Error(`Transition ${release.status} -> ${target} is not allowed`);
  }
  if (target === 'TESTING' && (!release.deploymentPlan || !release.rollbackPlan)) {
    throw new Error('A deployment plan and a rollback plan are required before testing');
  }
  if (target === 'GO_NO_GO' && !release.testSummary) {
    throw new Error('A test summary is required before the go / no-go review');
  }
  if (target === 'DEPLOYING') {
    if (release.goDecision !== 'GO') {
      throw new Error('A recorded GO decision is required before deployment');
    }
    const blocking = (content[id] ?? []).filter((entry) => !entry.deployable);
    if (blocking.length > 0) {
      throw new Error(
        `Every linked change must be approved before deployment: ${blocking
          .map((entry) => entry.number)
          .join(', ')}`,
      );
    }
    release.actualStart = release.actualStart ?? new Date().toISOString();
  }
  if (target === 'DEPLOYED' || target === 'ROLLED_BACK') {
    release.actualEnd = release.actualEnd ?? new Date().toISOString();
  }
  release.status = target;
  release.version += 1;
  return clone(release);
}

export function recordGoDecision(
  id: string,
  decision: ReleaseGoDecision,
  notes?: string,
): Release {
  const release = mustFind(id);
  if (release.status !== 'GO_NO_GO') {
    throw new Error('The go / no-go decision is only recorded during the GO_NO_GO review');
  }
  release.goDecision = decision;
  release.goDecisionNotes = notes ?? null;
  release.goDecidedBy = 'dev-local';
  release.goDecidedAt = new Date().toISOString();
  release.version += 1;
  return clone(release);
}

export function listReleaseContent(id: string): ReleaseContentEntry[] {
  mustFind(id);
  return (content[id] ?? []).map((entry) => ({ ...entry }));
}

export function linkReleaseChanges(
  id: string,
  changes: Array<{ id: string; number: string; title: string; type: string; status: string }>,
): ReleaseContentEntry[] {
  const release = mustFind(id);
  if (frozen(release.status)) {
    throw new Error('A release that reached deployment cannot change its content');
  }
  const current = content[id] ?? [];
  for (const change of changes) {
    if (current.some((entry) => entry.changeId === change.id)) continue;
    current.push({
      changeId: change.id,
      number: change.number,
      title: change.title,
      type: change.type,
      status: change.status,
      plannedStart: null,
      plannedEnd: null,
      deployable: DEPLOYABLE_CHANGE_STATES.has(change.status.toUpperCase()),
    });
  }
  content[id] = current;
  return listReleaseContent(id);
}

export function unlinkReleaseChange(id: string, changeId: string): ReleaseContentEntry[] {
  const release = mustFind(id);
  if (frozen(release.status)) {
    throw new Error('A release that reached deployment cannot change its content');
  }
  const current = content[id] ?? [];
  const index = current.findIndex((entry) => entry.changeId === changeId);
  if (index < 0) throw new Error(`Change is not part of this release: ${changeId}`);
  current.splice(index, 1);
  content[id] = current;
  return listReleaseContent(id);
}

export function availableTransitions(status: ReleaseStatus): ReleaseStatus[] {
  return [...ALLOWED[status]];
}

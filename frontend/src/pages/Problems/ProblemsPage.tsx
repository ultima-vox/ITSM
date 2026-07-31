import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { GitBranch, Plus, Search } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useT } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useDensity } from '@/hooks/useDensity';
import { useToast } from '@/hooks/useToast';
import {
  bulkAssignProblems,
  bulkSetProblemStatus,
  createProblem,
  fetchProblems,
  isLiveFeatureUnsupported,
  patchProblem,
  subscribeSecondaryModules,
  transitionProblemStatus,
} from '@/api';
import {
  Avatar,
  Button,
  ErrorState,
  Input,
  Modal,
  Select,
  Textarea,
} from '@/components/ui';
import { PriorityBadge, StatusChip } from '@/components/data-display';
import {
  ModuleDetailDrawer,
  type ModuleRelatedItem,
} from '@/components/modules/ModuleDetailDrawer';
import {
  ModuleGrid,
  type ModuleGridColumn,
  type ModuleGridSortDir,
} from '@/components/modules/ModuleGrid';
import { formatRelative } from '@/lib/format';
import {
  resolveRelatedHref,
  resolveRelatedLabel,
} from '@/lib/resolveRelated';
import {
  getProblemRuntimeTransitions,
  workflowStateLabelKey,
  type ProblemRuntimeTransition,
} from '@/lib/workflowRuntime';
import {
  getActiveWorkflowDefinition,
  subscribeWorkflowDefinitions,
} from '@/mock/workflow';
import { getModuleActivities } from '@/mock/store';
import type { Priority, Problem, WorkItemStatus } from '@/types';

type TranslateFn = (
  key: string,
  vars?: Record<string, string | number>,
) => string;

/** Forward path first; cancel last. Primary vs secondary hierarchy. */
const PROBLEM_ACTION_RANK: Record<string, number> = {
  in_progress: 0,
  resolved: 1,
  waiting: 2,
  closed: 3,
  cancelled: 4,
};

function problemActionVariant(
  status: WorkItemStatus | null,
): 'primary' | 'secondary' | 'danger' {
  if (status === 'cancelled') return 'danger';
  if (status === 'in_progress' || status === 'resolved') return 'primary';
  return 'secondary';
}

function problemRequiredFieldLabel(t: TranslateFn, field: string): string {
  const normalized = field.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
  const aliases: Record<string, string> = {
    assignee_id: 'assignee',
    root_cause: 'rootCause',
    rootcause: 'rootCause',
  };
  const logical = aliases[normalized] ?? normalized;
  const fieldKey = `problems.fields.${logical}`;
  const fromFields = t(fieldKey);
  if (fromFields !== fieldKey) return fromFields;
  const topKey = `problems.${logical}`;
  const fromTop = t(topKey);
  if (fromTop !== topKey) return fromTop;
  if (logical === 'assignee') return t('problems.colAssignee');
  return field;
}

function problemTransitionLabel(
  t: TranslateFn,
  tr: ProblemRuntimeTransition,
): string {
  const byKey = `problems.transition.${tr.key}`;
  const translated = t(byKey);
  if (translated !== byKey) return translated;
  if (tr.toStatus) {
    const byStatus = `problems.actions.to_${tr.toStatus}`;
    const s = t(byStatus);
    if (s !== byStatus) return s;
    return t(`status.${tr.toStatus}`);
  }
  return tr.to;
}

function problemTransitionDisabledReason(
  t: TranslateFn,
  tr: ProblemRuntimeTransition,
): string | undefined {
  if (tr.enabled) return undefined;
  if (tr.policyBlockKey) return t(tr.policyBlockKey);
  if (tr.unsupportedTarget) {
    return t('problems.workflowUnsupportedState', { state: tr.to });
  }
  if (tr.missingFields.length > 0) {
    const labels = tr.missingFields.map((f) => problemRequiredFieldLabel(t, f));
    return t('problems.workflowMissingFields', { fields: labels.join(', ') });
  }
  if (tr.missingPermissions?.length) {
    return t('problems.workflowMissingPermissions', {
      permissions: tr.missingPermissions.join(', '),
    });
  }
  return t('problems.workflowTransitionBlocked');
}

type SortKey = 'number' | 'priority' | 'status' | 'updated' | 'incidents';

const PRIORITY_RANK: Record<Priority, number> = {
  critical: 0,
  high: 1,
  medium: 2,
  low: 3,
};

const STATUS_RANK: Record<string, number> = {
  new: 0,
  in_progress: 1,
  waiting: 2,
  resolved: 3,
  closed: 4,
  cancelled: 5,
};

export function ProblemsPage() {
  const t = useT();
  const { isCompact, toggleDensity } = useDensity();
  const { success, error: toastError } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const idFromQuery = searchParams.get('id');
  const { data, loading, error, reload } = useAsync(() => fetchProblems(), []);
  const [query, setQuery] = useState('');
  const [priority, setPriority] = useState('');
  const [status, setStatus] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(idFromQuery);
  const [createOpen, setCreateOpen] = useState(false);
  const [sortKey, setSortKey] = useState<SortKey>('priority');
  const [sortDir, setSortDir] = useState<ModuleGridSortDir>('asc');
  const [validation, setValidation] = useState<string | null>(null);
  const [rootCauseDraft, setRootCauseDraft] = useState('');
  const [workaroundDraft, setWorkaroundDraft] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  /** Bumps when admin toggles active workflow version (session store). */
  const [workflowTick, setWorkflowTick] = useState(0);

  useEffect(() => {
    return subscribeSecondaryModules(() => reload());
  }, [reload]);

  useEffect(() => {
    return subscribeWorkflowDefinitions(() => {
      setWorkflowTick((n) => n + 1);
    });
  }, []);

  // Honor ?id= deep-link from search / related links
  useEffect(() => {
    if (!data?.length || !idFromQuery) return;
    if (data.some((p) => p.id === idFromQuery)) {
      setSelectedId(idFromQuery);
    }
  }, [data, idFromQuery]);

  const clearIdParam = useCallback(() => {
    if (!searchParams.has('id')) return;
    const next = new URLSearchParams(searchParams);
    next.delete('id');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const list = useMemo(() => {
    const filtered = (data ?? []).filter((p) => {
      if (priority && p.priority !== (priority as Priority)) return false;
      if (status && p.status !== (status as WorkItemStatus)) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return (
        p.number.toLowerCase().includes(q) ||
        p.title.toLowerCase().includes(q) ||
        (p.service?.toLowerCase().includes(q) ?? false)
      );
    });
    const dir = sortDir === 'asc' ? 1 : -1;
    filtered.sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'priority')
        cmp = PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority];
      else if (sortKey === 'status')
        cmp = (STATUS_RANK[a.status] ?? 9) - (STATUS_RANK[b.status] ?? 9);
      else if (sortKey === 'updated') cmp = a.updatedAt.localeCompare(b.updatedAt);
      else if (sortKey === 'incidents')
        cmp = a.relatedIncidents - b.relatedIncidents;
      else cmp = a.number.localeCompare(b.number);
      return cmp * dir;
    });
    return filtered;
  }, [data, query, priority, status, sortKey, sortDir]);

  const selected = useMemo(
    () =>
      selectedId
        ? (data ?? []).find((p) => p.id === selectedId) ?? null
        : null,
    [data, selectedId],
  );

  const handleBulkAssign = async () => {
    try {
      const n = await bulkAssignProblems([...selectedIds]);
      success(t('module.bulk.assigned', { n }));
      setSelectedIds(new Set());
    } catch (err) {
      toastError(
        isLiveFeatureUnsupported(err)
          ? t('module.errors.bulkLiveUnsupported')
          : t('module.errors.bulkFailed'),
      );
    }
  };

  const handleBulkStatus = async (next: WorkItemStatus) => {
    try {
      const n = await bulkSetProblemStatus([...selectedIds], next);
      if (n === 0) {
        toastError(t('module.errors.bulkNoneSucceeded'));
        return;
      }
      success(t('module.bulk.statusChanged', { n, status: t(`status.${next}`) }));
      setSelectedIds(new Set());
    } catch (err) {
      toastError(
        isLiveFeatureUnsupported(err)
          ? t('module.errors.bulkLiveUnsupported')
          : t('module.errors.bulkFailed'),
      );
    }
  };

  useEffect(() => {
    if (selected) {
      setRootCauseDraft(selected.rootCause ?? '');
      setWorkaroundDraft(selected.workaround ?? '');
    }
  }, [selected?.id]);

  const activities = useMemo(
    () => (selected ? getModuleActivities(selected.id) : []),
    [selected, data],
  );

  const related: ModuleRelatedItem[] = useMemo(() => {
    if (!selected) return [];
    const items: ModuleRelatedItem[] = [];
    selected.relatedWorkItemIds?.forEach((id) => {
      items.push({
        id,
        label: resolveRelatedLabel(id),
        meta: t('module.relatedWorkItem'),
        href: resolveRelatedHref(id) ?? `/work-items/${id}`,
      });
    });
    selected.relatedCiIds?.forEach((id) => {
      items.push({
        id,
        label: resolveRelatedLabel(id),
        meta: t('module.relatedCi'),
        href: resolveRelatedHref(id) ?? '/cmdb',
      });
    });
    if (selected.relatedIncidents > 0 && !selected.relatedWorkItemIds?.length) {
      items.push({
        id: 'inc-count',
        label: t('problems.relatedIncidentCount', {
          n: selected.relatedIncidents,
        }),
        meta: t('problems.colIncidents'),
      });
    }
    return items;
  }, [selected, t]);

  const toggleSort = (key: string) => {
    const k = key as SortKey;
    if (sortKey === k) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else {
      setSortKey(k);
      setSortDir(k === 'updated' ? 'desc' : 'asc');
    }
  };

  const openRow = useCallback((p: Problem) => {
    setSelectedId(p.id);
    setValidation(null);
  }, []);

  const runTransition = async (next: WorkItemStatus) => {
    if (!selected) return;
    setValidation(null);
    const result = await transitionProblemStatus(selected.id, next, {
      rootCause: rootCauseDraft,
      workaround: workaroundDraft,
    });
    if (!result.ok) {
      setValidation(t(result.errorKey));
      toastError(t(result.errorKey));
      return;
    }
    success(t('problems.transitionOk', { status: t(`status.${next}`) }));
    setSelectedId(result.problem.id);
  };

  const toggleKnownError = async () => {
    if (!selected) return;
    setValidation(null);
    const result = await patchProblem(selected.id, {
      knownError: !selected.knownError,
      rootCause: rootCauseDraft,
      workaround: workaroundDraft,
    });
    if (!result.ok) {
      setValidation(t(result.errorKey));
      toastError(t(result.errorKey));
      return;
    }
    success(
      result.problem.knownError
        ? t('problems.knownErrorMarked')
        : t('problems.knownErrorCleared'),
    );
  };

  const saveRca = async () => {
    if (!selected) return;
    const result = await patchProblem(selected.id, {
      rootCause: rootCauseDraft,
      workaround: workaroundDraft,
    });
    if (!result.ok) {
      setValidation(t(result.errorKey));
      return;
    }
    success(t('problems.rcaSaved'));
  };

  if (error && !loading && !data) {
    return (
      <section className="page">
        <div className="page-head">
          <div>
            <h1>{t('problems.title')}</h1>
            <p className="page-subtitle">{t('problems.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={reload} />
      </section>
    );
  }

  const columns = useMemo<ModuleGridColumn<Problem>[]>(
    () => [
      {
        id: 'number',
        header: t('problems.colNumber'),
        sortKey: 'number',
        width: 'minmax(90px, 0.8fr)',
        render: (p) => <b className="mono accent">{p.number}</b>,
      },
      {
        id: 'title',
        header: t('problems.colTitle'),
        width: 'minmax(160px, 1.8fr)',
        render: (p) => p.title,
      },
      {
        id: 'status',
        header: t('problems.colStatus'),
        sortKey: 'status',
        width: 'minmax(100px, 0.95fr)',
        render: (p) => <StatusChip status={p.status} />,
      },
      {
        id: 'priority',
        header: t('problems.colPriority'),
        sortKey: 'priority',
        width: 'minmax(90px, 0.85fr)',
        render: (p) => <PriorityBadge priority={p.priority} />,
      },
      {
        id: 'knownError',
        header: t('problems.colKnownError'),
        width: 'minmax(90px, 0.85fr)',
        render: (p) =>
          p.knownError ? (
            <span className="chip chip--warn">{t('problems.knownErrorYes')}</span>
          ) : (
            t('problems.knownErrorNo')
          ),
      },
      {
        id: 'incidents',
        header: t('problems.colIncidents'),
        sortKey: 'incidents',
        width: 'minmax(70px, 0.7fr)',
        render: (p) => p.relatedIncidents,
      },
      {
        id: 'assignee',
        header: t('problems.colAssignee'),
        width: 'minmax(120px, 1.1fr)',
        render: (p) =>
          p.assignee ? (
            <span className="inline-person">
              <Avatar initials={p.assignee.initials} size="sm" />
              {p.assignee.name}
            </span>
          ) : (
            <span className="muted">{t('overview.unassigned')}</span>
          ),
      },
      {
        id: 'updated',
        header: t('problems.colUpdated'),
        sortKey: 'updated',
        width: 'minmax(80px, 0.75fr)',
        className: 'muted',
        render: (p) => formatRelative(p.updatedAt, t),
      },
    ],
    [t],
  );

  // Active problem workflow (session) → next transitions; falls back when inactive.
  // workflowTick invalidates after admin active-version toggle.
  const wfRuntime = selected
    ? getProblemRuntimeTransitions(selected, {
        definition:
          workflowTick >= 0
            ? getActiveWorkflowDefinition('problem')
            : null,
        fieldOverrides: {
          rootCause: rootCauseDraft,
          workaround: workaroundDraft,
        },
      })
    : null;

  const runtimeTransitions = wfRuntime
    ? [...wfRuntime.transitions].sort(
        (a, b) =>
          (PROBLEM_ACTION_RANK[a.toStatus ?? ''] ?? 9) -
          (PROBLEM_ACTION_RANK[b.toStatus ?? ''] ?? 9),
      )
    : [];

  const workflowStateLabel = (() => {
    if (!wfRuntime) return '';
    const key = workflowStateLabelKey(wfRuntime.currentState, 'problems');
    const labeled = t(key);
    return labeled === key ? wfRuntime.currentState : labeled;
  })();

  const primaryTransitions = runtimeTransitions.filter(
    (tr) => problemActionVariant(tr.toStatus) === 'primary',
  );
  const secondaryTransitions = runtimeTransitions.filter(
    (tr) => problemActionVariant(tr.toStatus) !== 'primary',
  );

  return (
    <section className="page">
      <div className="page-head">
        <div>
          <h1>{t('problems.title')}</h1>
          <p className="page-subtitle">{t('problems.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">{list.length}</span>
          <button
            type="button"
            className={`chip chip--toggle${isCompact ? ' is-on' : ''}`}
            onClick={toggleDensity}
          >
            {isCompact ? t('app.densityCompact') : t('app.densityComfortable')}
          </button>
          <Button
            variant="primary"
            icon={<Plus size={18} />}
            onClick={() => setCreateOpen(true)}
          >
            {t('problems.create')}
          </Button>
        </div>
      </div>

      <div className="filters-bar filters-bar--module">
        <label className="field">
          <span className="field__label">{t('app.search')}</span>
          <div className="field__control">
            <Search size={16} aria-hidden />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('app.search')}
              aria-label={t('app.search')}
            />
          </div>
        </label>
        <Select
          label={t('problems.colPriority')}
          value={priority}
          onChange={(e) => setPriority(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'critical', label: t('priority.critical') },
            { value: 'high', label: t('priority.high') },
            { value: 'medium', label: t('priority.medium') },
            { value: 'low', label: t('priority.low') },
          ]}
        />
        <Select
          label={t('problems.colStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'new', label: t('status.new') },
            { value: 'in_progress', label: t('status.in_progress') },
            { value: 'waiting', label: t('status.waiting') },
            { value: 'resolved', label: t('status.resolved') },
            { value: 'closed', label: t('status.closed') },
          ]}
        />
      </div>

      <ModuleGrid
        rows={list}
        columns={columns}
        getRowId={(p) => p.id}
        getRowLabel={(p) => p.number}
        ariaLabel={t('problems.title')}
        loading={loading}
        sortKey={sortKey}
        sortDir={sortDir}
        onSort={toggleSort}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        onRowOpen={openRow}
        activeRowId={selectedId}
        emptyTitle={t('problems.emptyTitle')}
        emptyHint={t('problems.emptyHint')}
        emptyActionLabel={t('app.reset')}
        onEmptyAction={() => {
          setQuery('');
          setPriority('');
          setStatus('');
        }}
        onBulkAssign={() => void handleBulkAssign()}
        bulkActions={(
          ['in_progress', 'waiting', 'resolved', 'closed', 'cancelled'] as WorkItemStatus[]
        ).map((s) => (
          <button
            key={s}
            type="button"
            className="chip chip--toggle"
            onClick={() => void handleBulkStatus(s)}
          >
            {t(`status.${s}`)}
          </button>
        ))}
      />

      {selected && (
        <ModuleDetailDrawer
          open
          onClose={() => {
            setSelectedId(null);
            setValidation(null);
            clearIdParam();
          }}
          code={selected.number}
          title={selected.title}
          chips={
            <>
              <StatusChip status={selected.status} />
              {wfRuntime && (
                <span
                  className="chip chip--workflow"
                  title={
                    wfRuntime.definition
                      ? t('problems.workflowChipTitle', {
                          name:
                            wfRuntime.definition.name ??
                            wfRuntime.definition.objectKey,
                          version: wfRuntime.definition.version,
                          state: wfRuntime.currentState,
                        })
                      : t('problems.workflowFallbackChipTitle')
                  }
                >
                  <GitBranch size={12} aria-hidden />
                  {workflowStateLabel}
                  <span className="chip--workflow__key mono">
                    {wfRuntime.currentState}
                  </span>
                </span>
              )}
              <PriorityBadge priority={selected.priority} />
              {selected.knownError && (
                <span className="chip chip--warn">{t('problems.knownErrorYes')}</span>
              )}
            </>
          }
          validationMessage={validation}
          activities={activities}
          history={activities.filter(
            (a) => a.kind === 'field' || a.kind === 'status',
          )}
          related={related}
          relatedEmptyHint={t('module.relatedEmptyHint')}
          relatedEmptyAction={{
            label: t('problems.relatedEmptyCta'),
            href: '/queues',
          }}
          overview={
            <>
              <dl className="module-detail-dl">
                <div>
                  <dt>{t('problems.colIncidents')}</dt>
                  <dd>
                    {selected.relatedIncidents > 0
                      ? t('problems.relatedIncidentCount', {
                          n: selected.relatedIncidents,
                        })
                      : selected.relatedIncidents}
                  </dd>
                </div>
                <div>
                  <dt>{t('problems.colAssignee')}</dt>
                  <dd>
                    {selected.assignee ? (
                      <span className="inline-person">
                        <Avatar initials={selected.assignee.initials} size="sm" />
                        {selected.assignee.name}
                      </span>
                    ) : (
                      t('overview.unassigned')
                    )}
                  </dd>
                </div>
                {selected.service && (
                  <div>
                    <dt>{t('workItem.service')}</dt>
                    <dd>{selected.service}</dd>
                  </div>
                )}
                {selected.description && (
                  <div className="module-detail-dl__wide">
                    <dt>{t('workItem.description')}</dt>
                    <dd>{selected.description}</dd>
                  </div>
                )}
              </dl>
              <div className="module-rca">
                <Textarea
                  label={t('problems.rootCause')}
                  value={rootCauseDraft}
                  onChange={(e) => setRootCauseDraft(e.target.value)}
                  rows={3}
                  hint={t('problems.rootCauseHint')}
                />
                <Textarea
                  label={t('problems.workaround')}
                  value={workaroundDraft}
                  onChange={(e) => setWorkaroundDraft(e.target.value)}
                  rows={2}
                />
                <div className="module-rca__actions">
                  <Button size="sm" variant="secondary" onClick={() => void saveRca()}>
                    {t('problems.saveRca')}
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => void toggleKnownError()}>
                    {selected.knownError
                      ? t('problems.clearKnownError')
                      : t('problems.markKnownError')}
                  </Button>
                </div>
              </div>
            </>
          }
          actions={
            <div className="module-workflow" role="group" aria-label={t('problems.workflow')}>
              <div className="module-workflow__head">
                <p className="module-workflow__label">
                  <GitBranch size={14} aria-hidden />
                  {t('problems.workflow')}
                </p>
                <span className="module-workflow__meta muted">
                  {wfRuntime?.source === 'workflow' && wfRuntime.definition
                    ? t('problems.workflowSourceActive', {
                        name:
                          wfRuntime.definition.name ??
                          wfRuntime.definition.objectKey,
                        version: wfRuntime.definition.version,
                      })
                    : t('problems.workflowSourceFallback')}
                </span>
              </div>
              {runtimeTransitions.length > 0 ? (
                <div className="module-workflow__stack">
                  {primaryTransitions.length > 0 && (
                    <div className="module-workflow__primary">
                      {primaryTransitions.map((tr) => {
                        const reason = problemTransitionDisabledReason(t, tr);
                        const btn = (
                          <Button
                            size="sm"
                            variant={problemActionVariant(tr.toStatus)}
                            disabled={!tr.enabled || !tr.toStatus}
                            aria-disabled={!tr.enabled}
                            onClick={() => {
                              if (tr.enabled && tr.toStatus) {
                                void runTransition(tr.toStatus);
                              }
                            }}
                          >
                            {problemTransitionLabel(t, tr)}
                          </Button>
                        );
                        return reason ? (
                          <span
                            key={tr.key}
                            className="work-item-workflow__tip"
                            title={reason}
                          >
                            {btn}
                          </span>
                        ) : (
                          <span key={tr.key}>{btn}</span>
                        );
                      })}
                    </div>
                  )}
                  {secondaryTransitions.length > 0 && (
                    <div className="module-workflow__secondary">
                      {secondaryTransitions.map((tr) => {
                        const reason = problemTransitionDisabledReason(t, tr);
                        const btn = (
                          <Button
                            size="sm"
                            variant={problemActionVariant(tr.toStatus)}
                            disabled={!tr.enabled || !tr.toStatus}
                            aria-disabled={!tr.enabled}
                            onClick={() => {
                              if (tr.enabled && tr.toStatus) {
                                void runTransition(tr.toStatus);
                              }
                            }}
                          >
                            {problemTransitionLabel(t, tr)}
                          </Button>
                        );
                        return reason ? (
                          <span
                            key={tr.key}
                            className="work-item-workflow__tip"
                            title={reason}
                          >
                            {btn}
                          </span>
                        ) : (
                          <span key={tr.key}>{btn}</span>
                        );
                      })}
                    </div>
                  )}
                </div>
              ) : (
                <span className="muted">{t('problems.workflowNoTransitions')}</span>
              )}
            </div>
          }
        />
      )}

      <CreateProblemModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(p) => {
          setCreateOpen(false);
          success(t('problems.created', { number: p.number }));
          setSelectedId(p.id);
          reload();
        }}
      />
    </section>
  );
}

function CreateProblemModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (p: Problem) => void;
}) {
  const t = useT();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [service, setService] = useState('');
  const [priority, setPriority] = useState<Priority>('medium');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setTitle('');
      setDescription('');
      setService('');
      setPriority('medium');
      setErrors({});
      setSubmitting(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = t('problems.validation.title');
    setErrors(next);
    if (Object.keys(next).length) return;
    setSubmitting(true);
    try {
      const created = await createProblem({
        title: title.trim(),
        description: description.trim() || undefined,
        service: service.trim() || undefined,
        priority,
      });
      onCreated(created);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('problems.create')}
      labelledBy="create-problem-title"
    >
      <form className="module-create-form" onSubmit={(e) => void submit(e)}>
        <Input
          label={t('problems.colTitle')}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={errors.title}
          required
          autoFocus
        />
        <Textarea
          label={t('workItem.description')}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
        />
        <Input
          label={t('workItem.service')}
          value={service}
          onChange={(e) => setService(e.target.value)}
        />
        <Select
          label={t('problems.colPriority')}
          value={priority}
          onChange={(e) => setPriority(e.target.value as Priority)}
          options={[
            { value: 'critical', label: t('priority.critical') },
            { value: 'high', label: t('priority.high') },
            { value: 'medium', label: t('priority.medium') },
            { value: 'low', label: t('priority.low') },
          ]}
        />
        <div className="module-create-form__actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t('app.cancel')}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {t('app.create')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

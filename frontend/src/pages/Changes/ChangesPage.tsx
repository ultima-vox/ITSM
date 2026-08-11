import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  GitBranch,
  Plus,
  Search,
  ShieldAlert,
  Users,
} from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useDensity } from '@/hooks/useDensity';
import { useToast } from '@/hooks/useToast';
import {
  bulkAssignChanges,
  bulkSetChangeStatus,
  CAB_QUORUM_APPROVES,
  cabChairApproveAllowed,
  castCabMemberVote,
  countCabApproves,
  createChange,
  fetchChanges,
  fetchScheduleConflicts,
  isLiveFeatureUnsupported,
  patchChange,
  setChangeCabDecision,
  subscribeSecondaryModules,
  transitionChangeStatus,
  isMockMode,
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
import { formatDate, formatDateTime } from '@/lib/format';
import {
  resolveRelatedHref,
  resolveRelatedLabel,
} from '@/lib/resolveRelated';
import {
  getChangeRuntimeTransitions,
  workflowStateLabelKey,
  type ChangeRuntimeTransition,
} from '@/lib/workflowRuntime';
import {
  getActiveWorkflowDefinition,
  subscribeWorkflowDefinitions,
} from '@/mock/workflow';
import { getModuleActivities } from '@/mock/store';
import type {
  CabVoteDecision,
  Change,
  ChangeStatus,
  ChangeType,
  Priority,
} from '@/types';

type TranslateFn = (
  key: string,
  vars?: Record<string, string | number>,
) => string;

/** Prefer plannedStart; fall back to createdAt + 3d for missing schedule. */
function changeScheduleDate(c: Change): Date {
  if (c.plannedStart) return new Date(c.plannedStart);
  if (c.createdAt) {
    const d = new Date(c.createdAt);
    d.setDate(d.getDate() + 3);
    return d;
  }
  return new Date();
}

function localDayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function startOfWeekMonday(ref: Date): Date {
  const d = new Date(ref);
  d.setHours(0, 0, 0, 0);
  const day = d.getDay(); // 0 Sun … 6 Sat
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  return d;
}

function weekDaysFrom(monday: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + i);
    return d;
  });
}

interface ScheduleConflict {
  dayKey: string;
  ciIds: string[];
  changes: Change[];
}

/** Two+ NORMAL changes on same calendar day with intersecting related CIs. */
function findNormalScheduleConflicts(list: Change[]): ScheduleConflict[] {
  const active = list.filter(
    (c) =>
      c.type === 'normal' &&
      c.status !== 'cancelled' &&
      c.status !== 'completed',
  );
  const byDay = new Map<string, Change[]>();
  for (const c of active) {
    const key = localDayKey(changeScheduleDate(c));
    const arr = byDay.get(key) ?? [];
    arr.push(c);
    byDay.set(key, arr);
  }
  const out: ScheduleConflict[] = [];
  for (const [day, dayChanges] of byDay) {
    if (dayChanges.length < 2) continue;
    for (let i = 0; i < dayChanges.length; i++) {
      for (let j = i + 1; j < dayChanges.length; j++) {
        const a = dayChanges[i];
        const b = dayChanges[j];
        const aCis = new Set(a.relatedCiIds ?? []);
        const shared = (b.relatedCiIds ?? []).filter((id) => aCis.has(id));
        if (shared.length > 0) {
          out.push({
            dayKey: day,
            ciIds: shared,
            changes: [a, b],
          });
        }
      }
    }
  }
  return out;
}

const CHANGE_ACTION_RANK: Record<string, number> = {
  cab_review: 0,
  scheduled: 1,
  in_progress: 2,
  completed: 3,
  draft: 4,
  cancelled: 5,
};

function changeActionVariant(
  status: ChangeStatus | null,
): 'primary' | 'secondary' | 'danger' {
  if (status === 'cancelled') return 'danger';
  if (
    status === 'cab_review' ||
    status === 'scheduled' ||
    status === 'in_progress' ||
    status === 'completed'
  ) {
    return 'primary';
  }
  return 'secondary';
}

function changeRequiredFieldLabel(t: TranslateFn, field: string): string {
  const normalized = field.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
  const aliases: Record<string, string> = {
    assignee_id: 'assignee',
    implementation_plan: 'implementationPlan',
    implementationplan: 'implementationPlan',
    backout_plan: 'backoutPlan',
    backoutplan: 'backoutPlan',
    rollback_plan: 'backoutPlan',
    window_start: 'windowStart',
    window_end: 'windowEnd',
    planned_start: 'windowStart',
    planned_end: 'windowEnd',
  };
  const logical = aliases[normalized] ?? normalized;
  const fieldKey = `changes.fields.${logical}`;
  const fromFields = t(fieldKey);
  if (fromFields !== fieldKey) return fromFields;
  const topKey = `changes.${logical}`;
  const fromTop = t(topKey);
  if (fromTop !== topKey) return fromTop;
  if (logical === 'assignee') return t('changes.colAssignee');
  return field;
}

function changeTransitionLabel(
  t: TranslateFn,
  tr: ChangeRuntimeTransition,
): string {
  const byKey = `changes.transition.${tr.key}`;
  const translated = t(byKey);
  if (translated !== byKey) return translated;
  if (tr.toStatus) {
    const byStatus = `changes.actions.to_${tr.toStatus}`;
    const s = t(byStatus);
    if (s !== byStatus) return s;
    return t(`status.${tr.toStatus}`);
  }
  return tr.to;
}

function changeTransitionDisabledReason(
  t: TranslateFn,
  tr: ChangeRuntimeTransition,
): string | undefined {
  if (tr.enabled) return undefined;
  if (tr.policyBlockKey) return t(tr.policyBlockKey);
  if (tr.unsupportedTarget) {
    return t('changes.workflowUnsupportedState', { state: tr.to });
  }
  if (tr.missingFields.length > 0) {
    const labels = tr.missingFields.map((f) => changeRequiredFieldLabel(t, f));
    return t('changes.workflowMissingFields', { fields: labels.join(', ') });
  }
  if (tr.missingPermissions?.length) {
    return t('changes.workflowMissingPermissions', {
      permissions: tr.missingPermissions.join(', '),
    });
  }
  return t('changes.workflowTransitionBlocked');
}

type SortKey = 'number' | 'type' | 'status' | 'risk' | 'window';

const RISK_RANK: Record<Priority, number> = {
  critical: 0,
  high: 1,
  medium: 2,
  low: 3,
};

const STATUS_RANK: Record<string, number> = {
  draft: 0,
  cab_review: 1,
  scheduled: 2,
  in_progress: 3,
  completed: 4,
  cancelled: 5,
};

export function ChangesPage() {
  const t = useT();
  const { locale } = useI18n();
  const { isCompact, toggleDensity } = useDensity();
  const { success, error: toastError } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const idFromQuery = searchParams.get('id');
  const { data, loading, error, reload } = useAsync(() => fetchChanges(), []);
  const [query, setQuery] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(idFromQuery);
  const [createOpen, setCreateOpen] = useState(false);
  const [sortKey, setSortKey] = useState<SortKey>('window');
  const [sortDir, setSortDir] = useState<ModuleGridSortDir>('asc');
  const [validation, setValidation] = useState<string | null>(null);
  const [planDraft, setPlanDraft] = useState('');
  const [backoutDraft, setBackoutDraft] = useState('');
  const [cabNotesDraft, setCabNotesDraft] = useState('');
  const [riskDraft, setRiskDraft] = useState<Priority>('medium');
  /** 0 = current week; ±1 previous/next */
  const [weekOffset, setWeekOffset] = useState(0);
  const [cabBusyId, setCabBusyId] = useState<string | null>(null);
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
    if (data.some((c) => c.id === idFromQuery)) {
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
    const filtered = (data ?? []).filter((c) => {
      if (type && c.type !== type) return false;
      if (status && c.status !== status) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return (
        c.number.toLowerCase().includes(q) ||
        c.title.toLowerCase().includes(q) ||
        (c.service?.toLowerCase().includes(q) ?? false)
      );
    });
    const dir = sortDir === 'asc' ? 1 : -1;
    filtered.sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'risk') cmp = RISK_RANK[a.risk] - RISK_RANK[b.risk];
      else if (sortKey === 'status')
        cmp = (STATUS_RANK[a.status] ?? 9) - (STATUS_RANK[b.status] ?? 9);
      else if (sortKey === 'type') cmp = a.type.localeCompare(b.type);
      else if (sortKey === 'window')
        cmp = a.plannedStart.localeCompare(b.plannedStart);
      else cmp = a.number.localeCompare(b.number);
      return cmp * dir;
    });
    return filtered;
  }, [data, query, type, status, sortKey, sortDir]);

  const selected = useMemo(
    () =>
      selectedId
        ? (data ?? []).find((c) => c.id === selectedId) ?? null
        : null,
    [data, selectedId],
  );

  const handleBulkAssign = async () => {
    try {
      const n = await bulkAssignChanges([...selectedIds]);
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

  const handleBulkStatus = async (next: ChangeStatus) => {
    try {
      const result = await bulkSetChangeStatus([...selectedIds], next);
      if (result.ok === 0) {
        const first = result.skipped[0];
        toastError(
          first
            ? t('module.bulk.skippedDetail', {
                n: result.skipped.length,
                reason: t(first.errorKey),
              })
            : t('module.errors.bulkNoneSucceeded'),
        );
        return;
      }
      if (result.skipped.length > 0) {
        success(
          t('module.bulk.statusChangedPartial', {
            n: result.ok,
            skipped: result.skipped.length,
            status: t(`status.${next}`),
          }),
        );
      } else {
        success(
          t('module.bulk.statusChanged', {
            n: result.ok,
            status: t(`status.${next}`),
          }),
        );
      }
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
      setPlanDraft(selected.implementationPlan ?? '');
      setBackoutDraft(selected.backoutPlan ?? '');
      setCabNotesDraft(selected.cabNotes ?? '');
      setRiskDraft(selected.risk);
    }
  }, [selected]);

  const activities = useMemo(
    () => (selected ? getModuleActivities(selected.id) : []),
    [selected],
  );

  const related: ModuleRelatedItem[] = useMemo(() => {
    if (!selected) return [];
    const items: ModuleRelatedItem[] = [];
    selected.relatedWorkItemIds?.forEach((id) => {
      items.push({
        id,
        label: resolveRelatedLabel(id),
        meta: t('module.relatedWorkItem'),
        href: resolveRelatedHref(id),
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
    return items;
  }, [selected, t]);

  const allChanges = useMemo(() => data ?? [], [data]);
  const liveMode = !isMockMode();

  /** Client-side CI-overlap pairs (mock-friendly + always available). */
  const clientConflicts = useMemo(
    () => findNormalScheduleConflicts(allChanges),
    [allChanges],
  );

  const weekMonday = useMemo(() => {
    const base = startOfWeekMonday(new Date());
    base.setDate(base.getDate() + weekOffset * 7);
    return base;
  }, [weekOffset]);

  const weekDays = useMemo(() => weekDaysFrom(weekMonday), [weekMonday]);

  /** Live window conflicts from PostgreSQL schedule overlap. */
  const liveConflictsQ = useAsync(async () => {
    if (!liveMode || weekDays.length === 0) return [] as Change[];
    const start = weekDays[0];
    const end = new Date(weekDays[weekDays.length - 1]);
    end.setDate(end.getDate() + 1);
    try {
      return await fetchScheduleConflicts({
        start: start.toISOString(),
        end: end.toISOString(),
      });
    } catch {
      return [];
    }
  }, [liveMode, weekMonday]);

  /**
   * Banner conflicts: prefer client CI-pair analysis; when live API returns
   * overlapping changes not in client pairs, surface day-level flag via calendar.
   */
  const conflicts = clientConflicts;
  const liveConflictIds = useMemo(() => {
    const set = new Set<string>();
    for (const c of liveConflictsQ.data ?? []) set.add(c.id);
    return set;
  }, [liveConflictsQ.data]);

  const cabQueue = useMemo(
    () =>
      allChanges.filter(
        (c) => c.status === 'cab_review' && !c.cabApproved && !c.cabRejected,
      ),
    [allChanges],
  );

  const calendarByDay = useMemo(() => {
    const map = new Map<string, Change[]>();
    for (const d of weekDays) map.set(localDayKey(d), []);
    for (const c of allChanges) {
      if (c.status === 'cancelled' || c.status === 'completed') continue;
      if (
        c.status !== 'scheduled' &&
        c.status !== 'cab_review' &&
        c.status !== 'in_progress' &&
        c.status !== 'draft'
      ) {
        continue;
      }
      const key = localDayKey(changeScheduleDate(c));
      if (!map.has(key)) continue;
      map.get(key)!.push(c);
    }
    return map;
  }, [allChanges, weekDays]);

  const weekLabel = useMemo(() => {
    const end = new Date(weekMonday);
    end.setDate(weekMonday.getDate() + 6);
    return `${formatDate(weekMonday.toISOString(), locale)} – ${formatDate(end.toISOString(), locale)}`;
  }, [weekMonday, locale]);

  const runBoardCabDecision = async (
    changeId: string,
    decision: 'approve' | 'reject',
  ) => {
    setCabBusyId(changeId);
    try {
      const row = allChanges.find((change) => change.id === changeId);
      const result = await setChangeCabDecision(
        changeId, decision, undefined, row?.version ?? 0,
      );
      if (!result.ok) {
        toastError(t(result.errorKey));
        return;
      }
      success(
        decision === 'approve'
          ? t('changes.cab.approvedToast')
          : t('changes.cab.rejectedToast'),
      );
      if (selectedId === changeId) setSelectedId(result.change.id);
      reload();
    } finally {
      setCabBusyId(null);
    }
  };

  const toggleSort = (key: string) => {
    const k = key as SortKey;
    if (sortKey === k) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else {
      setSortKey(k);
      setSortDir('asc');
    }
  };

  const openRow = useCallback((c: Change) => {
    setSelectedId(c.id);
    setValidation(null);
  }, []);

  const savePlans = async () => {
    if (!selected) return;
    const result = await patchChange(selected.id, {
      implementationPlan: planDraft,
      backoutPlan: backoutDraft,
      expectedVersion: selected.version ?? 0,
    });
    if (!result.ok) {
      setValidation(t(result.errorKey));
      return;
    }
    success(t('changes.plansSaved'));
    await reload();
  };

  const saveCabFields = async () => {
    if (!selected) return;
    const result = await patchChange(selected.id, {
      risk: riskDraft,
      cabNotes: cabNotesDraft,
      expectedVersion: selected.version ?? 0,
    });
    if (!result.ok) {
      setValidation(t(result.errorKey));
      return;
    }
    success(t('changes.cab.fieldsSaved'));
    await reload();
  };

  const runCabDecision = async (decision: 'approve' | 'reject') => {
    if (!selected) return;
    setValidation(null);
    let expectedVersion = selected.version ?? 0;
    if (
      riskDraft !== selected.risk ||
      cabNotesDraft !== (selected.cabNotes ?? '')
    ) {
      const patched = await patchChange(selected.id, {
        risk: riskDraft,
        cabNotes: cabNotesDraft,
        expectedVersion,
      });
      if (!patched.ok) {
        setValidation(t(patched.errorKey));
        return;
      }
      expectedVersion = patched.change.version ?? expectedVersion + 1;
    }
    const result = await setChangeCabDecision(
      selected.id,
      decision,
      cabNotesDraft,
      expectedVersion,
    );
    if (!result.ok) {
      setValidation(t(result.errorKey));
      toastError(t(result.errorKey));
      return;
    }
    success(
      decision === 'approve'
        ? t('changes.cab.approvedToast')
        : t('changes.cab.rejectedToast'),
    );
    setSelectedId(result.change.id);
    await reload();
  };

  const runCabVote = async (memberId: string, decision: CabVoteDecision) => {
    if (!selected) return;
    const result = await castCabMemberVote(selected.id, memberId, decision);
    if (!result.ok) {
      toastError(t(result.errorKey));
      return;
    }
    success(t('changes.cab.voteRecorded'));
    setSelectedId(result.change.id);
  };

  const runTransition = async (next: ChangeStatus) => {
    if (!selected) return;
    setValidation(null);
    // Persist plans before transition so validation sees them
    let expectedVersion = selected.version ?? 0;
    if (
      planDraft !== selected.implementationPlan ||
      backoutDraft !== selected.backoutPlan
    ) {
      const patched = await patchChange(selected.id, {
        implementationPlan: planDraft,
        backoutPlan: backoutDraft,
        expectedVersion,
      });
      if (!patched.ok) {
        setValidation(t(patched.errorKey));
        return;
      }
      expectedVersion = patched.change.version ?? expectedVersion + 1;
    }
    const result = await transitionChangeStatus(
      selected.id, next, expectedVersion,
    );
    if (!result.ok) {
      setValidation(t(result.errorKey));
      toastError(t(result.errorKey));
      return;
    }
    success(t('changes.transitionOk', { status: t(`status.${next}`) }));
    setSelectedId(result.change.id);
    await reload();
  };

  const columns = useMemo<ModuleGridColumn<Change>[]>(
    () => [
      {
        id: 'number',
        header: t('changes.colNumber'),
        sortKey: 'number',
        width: 'minmax(90px, 0.8fr)',
        render: (c) => <b className="mono accent">{c.number}</b>,
      },
      {
        id: 'title',
        header: t('changes.colTitle'),
        width: 'minmax(150px, 1.6fr)',
        render: (c) => c.title,
      },
      {
        id: 'type',
        header: t('changes.colType'),
        sortKey: 'type',
        width: 'minmax(90px, 0.85fr)',
        render: (c) => (
          <span className="type-pill">{t(`changeType.${c.type}`)}</span>
        ),
      },
      {
        id: 'status',
        header: t('changes.colStatus'),
        sortKey: 'status',
        width: 'minmax(100px, 0.95fr)',
        render: (c) => <StatusChip status={c.status} />,
      },
      {
        id: 'risk',
        header: t('changes.colRisk'),
        sortKey: 'risk',
        width: 'minmax(90px, 0.85fr)',
        render: (c) => <PriorityBadge priority={c.risk} />,
      },
      {
        id: 'window',
        header: t('changes.colWindow'),
        sortKey: 'window',
        width: 'minmax(180px, 1.4fr)',
        className: 'window-cell',
        render: (c) => (
          <>
            {formatDateTime(c.plannedStart, locale)}
            <span className="muted"> {t('changes.windowTo')} </span>
            {formatDateTime(c.plannedEnd, locale)}
          </>
        ),
      },
      {
        id: 'assignee',
        header: t('changes.colAssignee'),
        width: 'minmax(110px, 1fr)',
        render: (c) =>
          c.assignee ? (
            <span className="inline-person">
              <Avatar initials={c.assignee.initials} size="sm" />
              {c.assignee.name}
            </span>
          ) : (
            <span className="muted">{t('overview.unassigned')}</span>
          ),
      },
    ],
    [t, locale],
  );

  if (error && !loading && !data) {
    return (
      <section className="page">
        <div className="page-head">
          <div>
            <h1>{t('changes.title')}</h1>
            <p className="page-subtitle">{t('changes.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={reload} />
      </section>
    );
  }

  // Active change workflow (session) → next transitions; falls back when inactive.
  // Policy gates (CAB required, plans) disable illegal edges with tooltips.
  const wfRuntime = selected
    ? getChangeRuntimeTransitions(selected, {
        definition:
          workflowTick >= 0 ? getActiveWorkflowDefinition('change') : null,
        fieldOverrides: {
          implementationPlan: planDraft,
          backoutPlan: backoutDraft,
        },
      })
    : null;

  const runtimeTransitions = wfRuntime
    ? [...wfRuntime.transitions].sort(
        (a, b) =>
          (CHANGE_ACTION_RANK[a.toStatus ?? ''] ?? 9) -
          (CHANGE_ACTION_RANK[b.toStatus ?? ''] ?? 9),
      )
    : [];

  const workflowStateLabel = (() => {
    if (!wfRuntime) return '';
    const key = workflowStateLabelKey(wfRuntime.currentState, 'changes');
    const labeled = t(key);
    return labeled === key ? wfRuntime.currentState : labeled;
  })();

  const primaryTransitions = runtimeTransitions.filter(
    (tr) => changeActionVariant(tr.toStatus) === 'primary',
  );
  const secondaryTransitions = runtimeTransitions.filter(
    (tr) => changeActionVariant(tr.toStatus) !== 'primary',
  );

  const showCabPanel =
    !!selected &&
    (selected.status === 'cab_review' ||
      selected.type === 'normal' ||
      selected.type === 'emergency' ||
      !!selected.cabVotes?.length);

  const emergencySkipWarning =
    !!selected &&
    selected.type === 'emergency' &&
    !selected.cabApproved &&
    !selected.cabRejected &&
    (selected.status === 'draft' ||
      selected.status === 'cab_review' ||
      selected.status === 'scheduled');

  return (
    <section className="page">
      <div className="page-head">
        <div>
          <h1>{t('changes.title')}</h1>
          <p className="page-subtitle">{t('changes.subtitle')}</p>
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
            {t('changes.create')}
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
          label={t('changes.colType')}
          value={type}
          onChange={(e) => setType(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'standard', label: t('changeType.standard') },
            { value: 'normal', label: t('changeType.normal') },
            { value: 'emergency', label: t('changeType.emergency') },
          ]}
        />
        <Select
          label={t('changes.colStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'draft', label: t('status.draft') },
            { value: 'cab_review', label: t('status.cab_review') },
            { value: 'scheduled', label: t('status.scheduled') },
            { value: 'in_progress', label: t('status.in_progress') },
            { value: 'completed', label: t('status.completed') },
            { value: 'cancelled', label: t('status.cancelled') },
          ]}
        />
      </div>

      {conflicts.length > 0 && (
        <div className="changes-conflict-banner" role="alert">
          <ShieldAlert size={18} aria-hidden />
          <div>
            <strong>{t('changes.conflict.title')}</strong>
            <p>
              {t('changes.conflict.body', {
                n: conflicts.length,
                pairs: conflicts
                  .map(
                    (c) =>
                      `${c.changes.map((x) => x.number).join(' ↔ ')} (${c.dayKey})`,
                  )
                  .join('; '),
              })}
            </p>
            <ul className="changes-conflict-banner__list">
              {conflicts.map((c) => (
                <li key={`${c.dayKey}-${c.changes.map((x) => x.id).join('-')}`}>
                  <button
                    type="button"
                    className="text-button"
                    onClick={() => openRow(c.changes[0])}
                  >
                    {c.changes.map((x) => x.number).join(' · ')}
                  </button>
                  <span className="muted">
                    {' '}
                    · {c.dayKey} ·{' '}
                    {t('changes.conflict.cis', {
                      names: c.ciIds.map((id) => resolveRelatedLabel(id)).join(', '),
                    })}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <div className="changes-ops-grid">
        <section className="panel changes-calendar" aria-labelledby="cab-cal-title">
          <div className="panel__header panel__header--dense">
            <div>
              <h2 id="cab-cal-title">{t('changes.calendar.title')}</h2>
              <p>{t('changes.calendar.hint')}</p>
            </div>
            <CalendarDays size={18} aria-hidden className="muted" />
          </div>
          <div className="changes-calendar__nav">
            <Button
              size="sm"
              variant="ghost"
              icon={<ChevronLeft size={16} />}
              aria-label={t('changes.calendar.prev')}
              onClick={() => setWeekOffset((o) => o - 1)}
            />
            <span className="changes-calendar__range">{weekLabel}</span>
            <Button
              size="sm"
              variant="ghost"
              icon={<ChevronRight size={16} />}
              aria-label={t('changes.calendar.next')}
              onClick={() => setWeekOffset((o) => o + 1)}
            />
            {weekOffset !== 0 && (
              <button
                type="button"
                className="text-button"
                onClick={() => setWeekOffset(0)}
              >
                {t('changes.calendar.today')}
              </button>
            )}
          </div>
          <div className="changes-calendar__week" role="grid">
            {weekDays.map((day) => {
              const key = localDayKey(day);
              const dayChanges = calendarByDay.get(key) ?? [];
              const isToday = key === localDayKey(new Date());
              const hasConflict =
                conflicts.some((c) => c.dayKey === key) ||
                dayChanges.some((ch) => liveConflictIds.has(ch.id));
              return (
                <div
                  key={key}
                  className={`changes-calendar__day${isToday ? ' is-today' : ''}${hasConflict ? ' is-conflict' : ''}`}
                  role="gridcell"
                >
                  <div className="changes-calendar__day-head">
                    <span>
                      {day.toLocaleDateString(locale, { weekday: 'short' })}
                    </span>
                    <b>{day.getDate()}</b>
                  </div>
                  <ul className="changes-calendar__events">
                    {dayChanges.length === 0 ? (
                      <li className="muted changes-calendar__empty">—</li>
                    ) : (
                      dayChanges.map((c) => (
                        <li key={c.id}>
                          <button
                            type="button"
                            className={`changes-calendar__chip changes-calendar__chip--${c.type}`}
                            onClick={() => openRow(c)}
                            title={c.title}
                          >
                            <span className="mono">{c.number}</span>
                            <small>{t(`changeType.${c.type}`)}</small>
                          </button>
                        </li>
                      ))
                    )}
                  </ul>
                </div>
              );
            })}
          </div>
        </section>

        <section className="panel changes-cab-board" aria-labelledby="cab-board-title">
          <div className="panel__header panel__header--dense">
            <div>
              <h2 id="cab-board-title">{t('changes.cabBoard.title')}</h2>
              <p>{t('changes.cabBoard.hint')}</p>
            </div>
            <Users size={18} aria-hidden className="muted" />
          </div>
          {cabQueue.length === 0 ? (
            <p className="muted changes-cab-board__empty">
              {t('changes.cabBoard.empty')}
            </p>
          ) : (
            <ul className="changes-cab-board__list">
              {cabQueue.map((c) => (
                <li key={c.id} className="changes-cab-board__row">
                  <button
                    type="button"
                    className="changes-cab-board__main"
                    onClick={() => openRow(c)}
                  >
                    <b className="mono accent">{c.number}</b>
                    <span className="changes-cab-board__title">{c.title}</span>
                    <PriorityBadge priority={c.risk} />
                    <span className="muted changes-cab-board__when">
                      {formatDateTime(changeScheduleDate(c).toISOString(), locale)}
                    </span>
                  </button>
                  <div className="changes-cab-board__actions">
                    {(() => {
                      const quorumOk = cabChairApproveAllowed(c);
                      const approves = countCabApproves(c);
                      return (
                        <>
                          <span
                            className="changes-cab-board__quorum muted"
                            title={t('changes.cab.quorumHint', {
                              n: CAB_QUORUM_APPROVES,
                              have: approves,
                            })}
                          >
                            {t('changes.cab.quorumChip', {
                              have: approves,
                              need: CAB_QUORUM_APPROVES,
                            })}
                          </span>
                          <span
                            title={
                              quorumOk
                                ? undefined
                                : t('changes.validation.cabQuorum')
                            }
                          >
                            <Button
                              size="sm"
                              variant="primary"
                              disabled={cabBusyId === c.id || !quorumOk}
                              onClick={() =>
                                void runBoardCabDecision(c.id, 'approve')
                              }
                            >
                              {t('changes.cab.approve')}
                            </Button>
                          </span>
                          <Button
                            size="sm"
                            variant="danger"
                            disabled={cabBusyId === c.id}
                            onClick={() =>
                              void runBoardCabDecision(c.id, 'reject')
                            }
                          >
                            {t('changes.cab.reject')}
                          </Button>
                        </>
                      );
                    })()}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <ModuleGrid
        rows={list}
        columns={columns}
        getRowId={(c) => c.id}
        getRowLabel={(c) => c.number}
        ariaLabel={t('changes.title')}
        loading={loading}
        sortKey={sortKey}
        sortDir={sortDir}
        onSort={toggleSort}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        onRowOpen={openRow}
        activeRowId={selectedId}
        emptyTitle={t('changes.emptyTitle')}
        emptyHint={t('changes.emptyHint')}
        emptyActionLabel={t('app.reset')}
        onEmptyAction={() => {
          setQuery('');
          setType('');
          setStatus('');
        }}
        onBulkAssign={() => void handleBulkAssign()}
        bulkActions={(
          [
            'cab_review',
            'scheduled',
            'in_progress',
            'completed',
            'cancelled',
          ] as ChangeStatus[]
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
                      ? t('changes.workflowChipTitle', {
                          name:
                            wfRuntime.definition.name ??
                            wfRuntime.definition.objectKey,
                          version: wfRuntime.definition.version,
                          state: wfRuntime.currentState,
                        })
                      : t('changes.workflowFallbackChipTitle')
                  }
                >
                  <GitBranch size={12} aria-hidden />
                  {workflowStateLabel}
                  <span className="chip--workflow__key mono">
                    {wfRuntime.currentState}
                  </span>
                </span>
              )}
              <span className="type-pill">{t(`changeType.${selected.type}`)}</span>
              <PriorityBadge priority={selected.risk} />
              {selected.cabApproved && (
                <span className="chip chip--ok">{t('changes.cabApproved')}</span>
              )}
              {selected.cabRejected && (
                <span className="chip chip--danger">{t('changes.cabRejected')}</span>
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
            label: t('module.relatedEmptyCta'),
            href: '/cmdb',
          }}
          overview={
            <>
              {emergencySkipWarning && (
                <div className="module-cab-banner" role="status">
                  <strong>{t('changes.cab.emergencyTitle')}</strong>
                  <p>{t('changes.cab.emergencyWarning')}</p>
                </div>
              )}
              <dl className="module-detail-dl">
                <div>
                  <dt>{t('changes.colWindow')}</dt>
                  <dd>
                    {formatDateTime(selected.plannedStart, locale)}
                    <br />→ {formatDateTime(selected.plannedEnd, locale)}
                  </dd>
                </div>
                <div>
                  <dt>{t('changes.colAssignee')}</dt>
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
                  label={t('changes.implementationPlan')}
                  value={planDraft}
                  onChange={(e) => setPlanDraft(e.target.value)}
                  rows={3}
                  hint={t('changes.planHint')}
                />
                <Textarea
                  label={t('changes.backoutPlan')}
                  value={backoutDraft}
                  onChange={(e) => setBackoutDraft(e.target.value)}
                  rows={2}
                  hint={t('changes.backoutHint')}
                />
                <div className="module-rca__actions">
                  <Button size="sm" variant="secondary" onClick={() => void savePlans()}>
                    {t('changes.savePlans')}
                  </Button>
                </div>
              </div>
              {showCabPanel && (
                <div className="module-cab" aria-labelledby="cab-panel-title">
                  <div className="module-cab__head">
                    <h3 id="cab-panel-title">{t('changes.cab.title')}</h3>
                    <p className="muted">{t('changes.cab.subtitle')}</p>
                  </div>
                  <Select
                    label={t('changes.cab.riskLevel')}
                    value={riskDraft}
                    onChange={(e) => setRiskDraft(e.target.value as Priority)}
                    options={[
                      { value: 'critical', label: t('priority.critical') },
                      { value: 'high', label: t('priority.high') },
                      { value: 'medium', label: t('priority.medium') },
                      { value: 'low', label: t('priority.low') },
                    ]}
                  />
                  <Textarea
                    label={t('changes.cab.notes')}
                    value={cabNotesDraft}
                    onChange={(e) => setCabNotesDraft(e.target.value)}
                    rows={2}
                    hint={t('changes.cab.notesHint')}
                  />
                  <div className="module-cab__members">
                    <h4>{t('changes.cab.members')}</h4>
                    <ul className="module-cab__member-list">
                      {(selected.cabVotes?.length
                        ? selected.cabVotes
                        : []
                      ).map((v) => (
                        <li key={v.memberId} className="module-cab__member">
                          <span className="inline-person">
                            <Avatar initials={v.initials} size="sm" />
                            <span>
                              <b>{v.memberName}</b>
                              {v.role && (
                                <small className="muted"> · {v.role}</small>
                              )}
                            </span>
                          </span>
                          <span className="module-cab__vote-actions">
                            {v.decision ? (
                              <span
                                className={`chip ${
                                  v.decision === 'approve'
                                    ? 'chip--ok'
                                    : v.decision === 'reject'
                                      ? 'chip--danger'
                                      : ''
                                }`}
                              >
                                {t(`changes.cab.vote.${v.decision}`)}
                              </span>
                            ) : (
                              <>
                                <Button
                                  size="sm"
                                  variant="secondary"
                                  onClick={() =>
                                    void runCabVote(v.memberId, 'approve')
                                  }
                                >
                                  {t('changes.cab.vote.approve')}
                                </Button>
                                <Button
                                  size="sm"
                                  variant="ghost"
                                  onClick={() =>
                                    void runCabVote(v.memberId, 'reject')
                                  }
                                >
                                  {t('changes.cab.vote.reject')}
                                </Button>
                              </>
                            )}
                          </span>
                        </li>
                      ))}
                      {(!selected.cabVotes || selected.cabVotes.length === 0) && (
                        <li className="muted">{t('changes.cab.noMembers')}</li>
                      )}
                    </ul>
                  </div>
                  <div className="module-cab__actions">
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => void saveCabFields()}
                    >
                      {t('changes.cab.saveFields')}
                    </Button>
                    <span
                      title={
                        cabChairApproveAllowed(selected) || selected.cabApproved
                          ? undefined
                          : t('changes.validation.cabQuorum')
                      }
                    >
                      <Button
                        size="sm"
                        variant="primary"
                        disabled={
                          !!selected.cabApproved ||
                          !cabChairApproveAllowed(selected)
                        }
                        onClick={() => void runCabDecision('approve')}
                      >
                        {t('changes.cab.approve')}
                      </Button>
                    </span>
                    <Button
                      size="sm"
                      variant="danger"
                      disabled={!!selected.cabRejected}
                      onClick={() => void runCabDecision('reject')}
                    >
                      {t('changes.cab.reject')}
                    </Button>
                  </div>
                  <p className="module-cab__hint muted">
                    {t('changes.cab.quorumHint', {
                      n: CAB_QUORUM_APPROVES,
                      have: countCabApproves(selected),
                    })}
                  </p>
                  {selected.type === 'normal' && !selected.cabApproved && (
                    <p className="module-cab__hint muted">
                      {t('changes.cab.normalRequiresApprove')}
                    </p>
                  )}
                </div>
              )}
            </>
          }
          actions={
            <div
              className="module-workflow"
              role="group"
              aria-label={t('changes.workflow')}
            >
              <div className="module-workflow__head">
                <p className="module-workflow__label">
                  <GitBranch size={14} aria-hidden />
                  {t('changes.workflow')}
                </p>
                <span className="module-workflow__meta muted">
                  {wfRuntime?.source === 'workflow' && wfRuntime.definition
                    ? t('changes.workflowSourceActive', {
                        name:
                          wfRuntime.definition.name ??
                          wfRuntime.definition.objectKey,
                        version: wfRuntime.definition.version,
                      })
                    : t('changes.workflowSourceFallback')}
                </span>
              </div>
              {runtimeTransitions.length > 0 ? (
                <div className="module-workflow__stack">
                  {primaryTransitions.length > 0 && (
                    <div className="module-workflow__primary">
                      {primaryTransitions.map((tr) => {
                        const reason = changeTransitionDisabledReason(t, tr);
                        const btn = (
                          <Button
                            size="sm"
                            variant={changeActionVariant(tr.toStatus)}
                            disabled={!tr.enabled || !tr.toStatus}
                            aria-disabled={!tr.enabled}
                            onClick={() => {
                              if (tr.enabled && tr.toStatus) {
                                void runTransition(tr.toStatus);
                              }
                            }}
                          >
                            {changeTransitionLabel(t, tr)}
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
                        const reason = changeTransitionDisabledReason(t, tr);
                        const btn = (
                          <Button
                            size="sm"
                            variant={changeActionVariant(tr.toStatus)}
                            disabled={!tr.enabled || !tr.toStatus}
                            aria-disabled={!tr.enabled}
                            onClick={() => {
                              if (tr.enabled && tr.toStatus) {
                                void runTransition(tr.toStatus);
                              }
                            }}
                          >
                            {changeTransitionLabel(t, tr)}
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
                <span className="muted">{t('changes.workflowNoTransitions')}</span>
              )}
            </div>
          }
        />
      )}

      <CreateChangeModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(c) => {
          setCreateOpen(false);
          success(t('changes.created', { number: c.number }));
          setSelectedId(c.id);
          reload();
        }}
      />
    </section>
  );
}

function CreateChangeModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (c: Change) => void;
}) {
  const t = useT();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [service, setService] = useState('');
  const [type, setType] = useState<ChangeType>('normal');
  const [risk, setRisk] = useState<Priority>('medium');
  const [plan, setPlan] = useState('');
  const [backout, setBackout] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setTitle('');
      setDescription('');
      setService('');
      setType('normal');
      setRisk('medium');
      setPlan('');
      setBackout('');
      setErrors({});
      setSubmitting(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!title.trim()) next.title = t('changes.validation.title');
    setErrors(next);
    if (Object.keys(next).length) return;
    setSubmitting(true);
    try {
      const created = await createChange({
        title: title.trim(),
        description: description.trim() || undefined,
        service: service.trim() || undefined,
        type,
        risk,
        implementationPlan: plan.trim() || undefined,
        backoutPlan: backout.trim() || undefined,
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
      title={t('changes.create')}
      labelledBy="create-change-title"
      size="lg"
    >
      <form className="module-create-form" onSubmit={(e) => void submit(e)}>
        <Input
          label={t('changes.colTitle')}
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
          rows={2}
        />
        <div className="module-create-form__row">
          <Select
            label={t('changes.colType')}
            value={type}
            onChange={(e) => setType(e.target.value as ChangeType)}
            options={[
              { value: 'standard', label: t('changeType.standard') },
              { value: 'normal', label: t('changeType.normal') },
              { value: 'emergency', label: t('changeType.emergency') },
            ]}
          />
          <Select
            label={t('changes.colRisk')}
            value={risk}
            onChange={(e) => setRisk(e.target.value as Priority)}
            options={[
              { value: 'critical', label: t('priority.critical') },
              { value: 'high', label: t('priority.high') },
              { value: 'medium', label: t('priority.medium') },
              { value: 'low', label: t('priority.low') },
            ]}
          />
        </div>
        <Input
          label={t('workItem.service')}
          value={service}
          onChange={(e) => setService(e.target.value)}
        />
        <Textarea
          label={t('changes.implementationPlan')}
          value={plan}
          onChange={(e) => setPlan(e.target.value)}
          rows={2}
        />
        <Textarea
          label={t('changes.backoutPlan')}
          value={backout}
          onChange={(e) => setBackout(e.target.value)}
          rows={2}
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

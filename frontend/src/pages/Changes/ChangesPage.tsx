import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react';
import { ArrowDown, ArrowUp, ChevronUp, Plus, Search } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useDensity } from '@/hooks/useDensity';
import { useToast } from '@/hooks/useToast';
import {
  createChange,
  fetchChanges,
  getChangeTransitions,
  patchChange,
  subscribeSecondaryModules,
  transitionChangeStatus,
} from '@/api';
import {
  Avatar,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Select,
  SkeletonRows,
  Textarea,
} from '@/components/ui';
import { PriorityBadge, StatusChip } from '@/components/data-display';
import {
  ModuleDetailDrawer,
  type ModuleRelatedItem,
} from '@/components/modules/ModuleDetailDrawer';
import { formatDateTime } from '@/lib/format';
import {
  resolveRelatedHref,
  resolveRelatedLabel,
} from '@/lib/resolveRelated';
import { getModuleActivities } from '@/mock/store';
import type { Change, ChangeStatus, ChangeType, Priority } from '@/types';

const CHANGE_ACTION_RANK: Record<string, number> = {
  cab_review: 0,
  scheduled: 1,
  in_progress: 2,
  completed: 3,
  draft: 4,
  cancelled: 5,
};

function changeActionVariant(
  status: ChangeStatus,
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

type SortKey = 'number' | 'type' | 'status' | 'risk' | 'window';
type SortDir = 'asc' | 'desc';

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
  const { data, loading, error, reload } = useAsync(() => fetchChanges(), []);
  const [query, setQuery] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [sortKey, setSortKey] = useState<SortKey>('window');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [focusIndex, setFocusIndex] = useState(-1);
  const [validation, setValidation] = useState<string | null>(null);
  const [planDraft, setPlanDraft] = useState('');
  const [backoutDraft, setBackoutDraft] = useState('');
  const listRef = useRef<HTMLTableSectionElement>(null);

  useEffect(() => {
    return subscribeSecondaryModules(() => reload());
  }, [reload]);

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

  useEffect(() => {
    if (selected) {
      setPlanDraft(selected.implementationPlan ?? '');
      setBackoutDraft(selected.backoutPlan ?? '');
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

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const openRow = useCallback((c: Change) => {
    setSelectedId(c.id);
    setValidation(null);
  }, []);

  const onListKeyDown = (e: ReactKeyboardEvent) => {
    if (list.length === 0) return;
    const key = e.key.toLowerCase();
    if (e.key === 'ArrowDown' || key === 'j') {
      e.preventDefault();
      setFocusIndex((i) => Math.min(i < 0 ? 0 : i + 1, list.length - 1));
    } else if (e.key === 'ArrowUp' || key === 'k') {
      e.preventDefault();
      setFocusIndex((i) => Math.max(i < 0 ? 0 : i - 1, 0));
    } else if (e.key === 'Enter' && focusIndex >= 0) {
      e.preventDefault();
      openRow(list[focusIndex]);
    } else if (e.key === 'Escape') setFocusIndex(-1);
  };

  useEffect(() => {
    if (focusIndex < 0) return;
    const el = listRef.current?.querySelector<HTMLElement>(
      `[data-row-index="${focusIndex}"]`,
    );
    el?.scrollIntoView({ block: 'nearest' });
  }, [focusIndex]);

  const savePlans = async () => {
    if (!selected) return;
    const result = await patchChange(selected.id, {
      implementationPlan: planDraft,
      backoutPlan: backoutDraft,
    });
    if (!result.ok) {
      setValidation(t(result.errorKey));
      return;
    }
    success(t('changes.plansSaved'));
  };

  const runTransition = async (next: ChangeStatus) => {
    if (!selected) return;
    setValidation(null);
    // Persist plans before transition so validation sees them
    if (planDraft !== selected.implementationPlan || backoutDraft !== selected.backoutPlan) {
      await patchChange(selected.id, {
        implementationPlan: planDraft,
        backoutPlan: backoutDraft,
      });
    }
    const result = await transitionChangeStatus(selected.id, next);
    if (!result.ok) {
      setValidation(t(result.errorKey));
      toastError(t(result.errorKey));
      return;
    }
    success(t('changes.transitionOk', { status: t(`status.${next}`) }));
    setSelectedId(result.change.id);
  };

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

  const SortIcon = ({ col }: { col: SortKey }) => {
    if (sortKey !== col)
      return <ChevronUp size={12} className="sort-icon sort-icon--idle" />;
    return sortDir === 'asc' ? (
      <ArrowUp size={12} className="sort-icon" />
    ) : (
      <ArrowDown size={12} className="sort-icon" />
    );
  };

  const transitions = selected ? getChangeTransitions(selected.status) : [];

  // Filter schedule action for non-standard from draft (store will reject; hide for UX)
  const visibleTransitions = transitions
    .filter((s) => {
      if (!selected) return false;
      if (
        s === 'scheduled' &&
        selected.type !== 'standard' &&
        selected.status === 'draft'
      ) {
        return false;
      }
      return true;
    })
    .sort(
      (a, b) =>
        (CHANGE_ACTION_RANK[a] ?? 9) - (CHANGE_ACTION_RANK[b] ?? 9),
    );

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

      {!loading && list.length > 0 && (
        <div className="grid-kbd-hint" aria-hidden>
          <kbd>↑</kbd>
          <kbd>↓</kbd>
          <span>/</span>
          <kbd>J</kbd>
          <kbd>K</kbd>
          <span>{t('grid.kbdNav')}</span>
          <kbd>Enter</kbd>
          <span>{t('grid.kbdOpen')}</span>
        </div>
      )}

      <div
        className={`panel panel--flush data-table-wrap module-table${
          isCompact ? ' is-compact' : ''
        }`}
      >
        <table
          className="data-table data-table--clickable data-table--sortable"
          aria-label={t('changes.title')}
        >
          <thead>
            <tr>
              <th scope="col">
                <button type="button" className="th-sort" onClick={() => toggleSort('number')}>
                  {t('changes.colNumber')}
                  <SortIcon col="number" />
                </button>
              </th>
              <th scope="col">{t('changes.colTitle')}</th>
              <th scope="col">
                <button type="button" className="th-sort" onClick={() => toggleSort('type')}>
                  {t('changes.colType')}
                  <SortIcon col="type" />
                </button>
              </th>
              <th scope="col">
                <button type="button" className="th-sort" onClick={() => toggleSort('status')}>
                  {t('changes.colStatus')}
                  <SortIcon col="status" />
                </button>
              </th>
              <th scope="col">
                <button type="button" className="th-sort" onClick={() => toggleSort('risk')}>
                  {t('changes.colRisk')}
                  <SortIcon col="risk" />
                </button>
              </th>
              <th scope="col">
                <button type="button" className="th-sort" onClick={() => toggleSort('window')}>
                  {t('changes.colWindow')}
                  <SortIcon col="window" />
                </button>
              </th>
              <th scope="col">{t('changes.colAssignee')}</th>
            </tr>
          </thead>
          <tbody ref={listRef} tabIndex={0} onKeyDown={onListKeyDown}>
            {loading ? (
              <tr>
                <td colSpan={7}>
                  <SkeletonRows rows={3} />
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan={7}>
                  <EmptyState
                    title={t('changes.emptyTitle')}
                    description={t('changes.emptyHint')}
                    actionLabel={t('app.reset')}
                    onAction={() => {
                      setQuery('');
                      setType('');
                      setStatus('');
                    }}
                  />
                </td>
              </tr>
            ) : (
              list.map((c, index) => (
                <tr
                  key={c.id}
                  tabIndex={focusIndex === index ? 0 : -1}
                  data-row-index={index}
                  className={
                    focusIndex === index
                      ? 'is-focused'
                      : selectedId === c.id
                        ? 'is-selected'
                        : undefined
                  }
                  onClick={() => openRow(c)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      openRow(c);
                    }
                  }}
                >
                  <td>
                    <b className="mono accent">{c.number}</b>
                  </td>
                  <td>{c.title}</td>
                  <td>
                    <span className="type-pill">{t(`changeType.${c.type}`)}</span>
                  </td>
                  <td>
                    <StatusChip status={c.status} />
                  </td>
                  <td>
                    <PriorityBadge priority={c.risk} />
                  </td>
                  <td className="window-cell">
                    {formatDateTime(c.plannedStart, locale)}
                    <span className="muted"> {t('changes.windowTo')} </span>
                    {formatDateTime(c.plannedEnd, locale)}
                  </td>
                  <td>
                    {c.assignee ? (
                      <span className="inline-person">
                        <Avatar initials={c.assignee.initials} size="sm" />
                        {c.assignee.name}
                      </span>
                    ) : (
                      <span className="muted">{t('overview.unassigned')}</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {selected && (
        <ModuleDetailDrawer
          open
          onClose={() => {
            setSelectedId(null);
            setValidation(null);
          }}
          code={selected.number}
          title={selected.title}
          chips={
            <>
              <StatusChip status={selected.status} />
              <span className="type-pill">{t(`changeType.${selected.type}`)}</span>
              <PriorityBadge priority={selected.risk} />
              {selected.cabApproved && (
                <span className="chip chip--ok">{t('changes.cabApproved')}</span>
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
            </>
          }
          actions={
            visibleTransitions.length > 0 ? (
              <div className="module-workflow__stack">
                <div className="module-workflow__primary">
                  {visibleTransitions
                    .filter((s) => changeActionVariant(s) === 'primary')
                    .map((s) => (
                      <Button
                        key={s}
                        size="sm"
                        variant="primary"
                        onClick={() => void runTransition(s)}
                      >
                        {t(`changes.actions.to_${s}`)}
                      </Button>
                    ))}
                </div>
                <div className="module-workflow__secondary">
                  {visibleTransitions
                    .filter((s) => changeActionVariant(s) !== 'primary')
                    .map((s) => (
                      <Button
                        key={s}
                        size="sm"
                        variant={changeActionVariant(s)}
                        onClick={() => void runTransition(s)}
                      >
                        {t(`changes.actions.to_${s}`)}
                      </Button>
                    ))}
                </div>
              </div>
            ) : (
              <span className="muted">{t('module.noTransitions')}</span>
            )
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

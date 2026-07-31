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
  bulkAssignAssets,
  bulkSetAssetStatus,
  createAsset,
  fetchAssets,
  getAssetTransitions,
  subscribeSecondaryModules,
  transitionAssetStatus,
} from '@/api';
import {
  Button,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Select,
  SkeletonRows,
  Textarea,
} from '@/components/ui';
import { StatusChip } from '@/components/data-display';
import {
  ModuleDetailDrawer,
  type ModuleRelatedItem,
} from '@/components/modules/ModuleDetailDrawer';
import { formatDate } from '@/lib/format';
import {
  resolveRelatedHref,
  resolveRelatedLabel,
} from '@/lib/resolveRelated';
import { getModuleActivities } from '@/mock/store';
import type { Asset, AssetStatus } from '@/types';

type SortKey = 'tag' | 'name' | 'status' | 'location' | 'purchased';
type SortDir = 'asc' | 'desc';

const STATUS_RANK: Record<string, number> = {
  repair: 0,
  in_use: 1,
  stock: 2,
  retired: 3,
};

export function AssetsPage() {
  const t = useT();
  const { locale } = useI18n();
  const { isCompact, toggleDensity } = useDensity();
  const { success, error: toastError } = useToast();
  const { data, loading, error, reload } = useAsync(() => fetchAssets(), []);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [sortKey, setSortKey] = useState<SortKey>('tag');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [focusIndex, setFocusIndex] = useState(-1);
  const [validation, setValidation] = useState<string | null>(null);
  const [assignName, setAssignName] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const listRef = useRef<HTMLTableSectionElement>(null);

  useEffect(() => {
    return subscribeSecondaryModules(() => reload());
  }, [reload]);

  const list = useMemo(() => {
    const filtered = (data ?? []).filter((a) => {
      if (status && a.status !== status) return false;
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return (
        a.tag.toLowerCase().includes(q) ||
        a.name.toLowerCase().includes(q) ||
        a.location.toLowerCase().includes(q) ||
        (a.assignedTo?.toLowerCase().includes(q) ?? false)
      );
    });
    const dir = sortDir === 'asc' ? 1 : -1;
    filtered.sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'status') {
        cmp = (STATUS_RANK[a.status] ?? 9) - (STATUS_RANK[b.status] ?? 9);
      } else if (sortKey === 'tag') cmp = a.tag.localeCompare(b.tag);
      else if (sortKey === 'name') cmp = a.name.localeCompare(b.name);
      else if (sortKey === 'location') cmp = a.location.localeCompare(b.location);
      else cmp = a.purchasedAt.localeCompare(b.purchasedAt);
      return cmp * dir;
    });
    return filtered;
  }, [data, query, status, sortKey, sortDir]);

  useEffect(() => {
    setSelectedIds((prev) => {
      const ids = new Set(list.map((a) => a.id));
      const next = new Set([...prev].filter((id) => ids.has(id)));
      return next.size === prev.size ? prev : next;
    });
  }, [list]);

  const selected = useMemo(
    () => (selectedId ? (data ?? []).find((a) => a.id === selectedId) ?? null : null),
    [data, selectedId],
  );

  const allSelected = list.length > 0 && selectedIds.size === list.length;
  const someSelected = selectedIds.size > 0 && !allSelected;

  const toggleAll = () => {
    if (allSelected) setSelectedIds(new Set());
    else setSelectedIds(new Set(list.map((a) => a.id)));
  };

  const toggleOne = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const ariaSortFor = (col: SortKey): 'ascending' | 'descending' | 'none' => {
    if (sortKey !== col) return 'none';
    return sortDir === 'asc' ? 'ascending' : 'descending';
  };

  const handleBulkAssign = async () => {
    const ids = [...selectedIds];
    const n = await bulkAssignAssets(ids);
    success(t('module.bulk.assigned', { n }));
    setSelectedIds(new Set());
  };

  const handleBulkStatus = async (next: AssetStatus) => {
    const ids = [...selectedIds];
    const n = await bulkSetAssetStatus(ids, next);
    success(t('module.bulk.statusChanged', { n, status: t(`status.${next}`) }));
    setSelectedIds(new Set());
  };

  const activities = useMemo(
    () => (selected ? getModuleActivities(selected.id) : []),
    [selected, data],
  );

  const related: ModuleRelatedItem[] = useMemo(() => {
    if (!selected?.relatedCiIds?.length) return [];
    return selected.relatedCiIds.map((id) => ({
      id,
      label: resolveRelatedLabel(id),
      meta: t('module.relatedCi'),
      href: resolveRelatedHref(id) ?? '/cmdb',
    }));
  }, [selected, t]);

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const openRow = useCallback((a: Asset) => {
    setSelectedId(a.id);
    setValidation(null);
    setAssignName(a.assignedTo ?? '');
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
    } else if (e.key === ' ' && focusIndex >= 0) {
      e.preventDefault();
      toggleOne(list[focusIndex].id);
    } else if ((e.metaKey || e.ctrlKey) && key === 'a') {
      e.preventDefault();
      toggleAll();
    } else if (e.key === 'Escape') {
      setFocusIndex(-1);
      setSelectedIds(new Set());
    }
  };

  useEffect(() => {
    if (focusIndex < 0) return;
    const el = listRef.current?.querySelector<HTMLElement>(
      `[data-row-index="${focusIndex}"]`,
    );
    el?.scrollIntoView({ block: 'nearest' });
  }, [focusIndex]);

  const runTransition = async (next: AssetStatus) => {
    if (!selected) return;
    setValidation(null);
    const result = await transitionAssetStatus(selected.id, next, {
      assignedTo: assignName.trim() || selected.assignedTo,
    });
    if (!result.ok) {
      setValidation(t(result.errorKey));
      toastError(t(result.errorKey));
      return;
    }
    success(t('assets.transitionOk', { status: t(`status.${next}`) }));
    setSelectedId(result.asset.id);
  };

  if (error && !loading && !data) {
    return (
      <section className="page">
        <div className="page-head">
          <div>
            <h1>{t('assets.title')}</h1>
            <p className="page-subtitle">{t('assets.subtitle')}</p>
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

  const transitions = selected ? getAssetTransitions(selected.status) : [];

  return (
    <section className="page">
      <div className="page-head">
        <div>
          <h1>{t('assets.title')}</h1>
          <p className="page-subtitle">{t('assets.subtitle')}</p>
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
            {t('assets.addAsset')}
          </Button>
        </div>
      </div>

      <div className="module-toolbar">
        <label className="field" style={{ flex: '1 1 220px', maxWidth: 360 }}>
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
          label={t('assets.colStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={[
            { value: '', label: t('app.all') },
            { value: 'in_use', label: t('status.in_use') },
            { value: 'stock', label: t('status.stock') },
            { value: 'repair', label: t('status.repair') },
            { value: 'retired', label: t('status.retired') },
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
          <kbd>Space</kbd>
          <span>{t('grid.kbdSelect')}</span>
          <kbd>Ctrl</kbd>
          <kbd>A</kbd>
          <span>{t('grid.selectAll')}</span>
        </div>
      )}

      {selectedIds.size > 0 && (
        <div className="bulk-bar" role="toolbar" aria-label={t('grid.bulkActions')}>
          <span className="bulk-bar__count">
            {t('grid.selected', { n: selectedIds.size })}
          </span>
          <Button variant="secondary" size="sm" onClick={() => void handleBulkAssign()}>
            {t('grid.assignToMe')}
          </Button>
          <div className="bulk-bar__priority">
            <span>{t('module.bulk.changeStatus')}</span>
            {(['in_use', 'stock', 'repair', 'retired'] as AssetStatus[]).map((s) => (
              <button
                key={s}
                type="button"
                className="chip chip--toggle"
                onClick={() => void handleBulkStatus(s)}
              >
                {t(`status.${s}`)}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="text-link bulk-bar__clear"
            onClick={() => setSelectedIds(new Set())}
          >
            {t('grid.clearSelection')}
          </button>
        </div>
      )}

      <div
        className={`panel panel--flush data-table-wrap module-table${
          isCompact ? ' is-compact' : ''
        }`}
      >
        <table
          className="data-table data-table--clickable data-table--sortable"
          aria-label={t('assets.title')}
        >
          <thead>
            <tr>
              <th scope="col" className="grid-check">
                <label onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    checked={allSelected}
                    ref={(el) => {
                      if (el) el.indeterminate = someSelected;
                    }}
                    onChange={toggleAll}
                    aria-label={t('grid.selectAll')}
                  />
                </label>
              </th>
              {(
                [
                  ['tag', 'assets.colTag'],
                  ['name', 'assets.colName'],
                  ['status', 'assets.colStatus'],
                  ['location', 'assets.colLocation'],
                ] as [SortKey, string][]
              ).map(([key, labelKey]) => (
                <th key={key} scope="col" aria-sort={ariaSortFor(key)}>
                  <button
                    type="button"
                    className="th-sort"
                    onClick={() => toggleSort(key)}
                  >
                    {t(labelKey)}
                    <SortIcon col={key} />
                  </button>
                </th>
              ))}
              <th scope="col">{t('assets.colType')}</th>
              <th scope="col">{t('assets.colAssignee')}</th>
            </tr>
          </thead>
          <tbody ref={listRef} tabIndex={0} onKeyDown={onListKeyDown}>
            {loading ? (
              <tr>
                <td colSpan={7}>
                  <SkeletonRows rows={4} />
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan={7}>
                  <EmptyState
                    title={t('assets.emptyTitle')}
                    description={t('assets.emptyHint')}
                    actionLabel={t('app.reset')}
                    onAction={() => {
                      setQuery('');
                      setStatus('');
                    }}
                  />
                </td>
              </tr>
            ) : (
              list.map((a, index) => {
                const isChecked = selectedIds.has(a.id);
                return (
                  <tr
                    key={a.id}
                    tabIndex={focusIndex === index ? 0 : -1}
                    data-row-index={index}
                    aria-selected={isChecked}
                    className={[
                      focusIndex === index ? 'is-focused' : '',
                      selectedId === a.id || isChecked ? 'is-selected' : '',
                    ]
                      .filter(Boolean)
                      .join(' ') || undefined}
                    onClick={() => openRow(a)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        openRow(a);
                      }
                    }}
                  >
                    <td className="grid-check">
                      <label
                        onClick={(e) => e.stopPropagation()}
                        onKeyDown={(e) => e.stopPropagation()}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => toggleOne(a.id)}
                          aria-label={t('grid.selectRow', { n: a.tag })}
                        />
                      </label>
                    </td>
                    <td>
                      <b className="mono">{a.tag}</b>
                    </td>
                    <td>
                      {a.name}
                      <small className="cell-sub">
                        {formatDate(a.purchasedAt, locale)}
                      </small>
                    </td>
                    <td>
                      <StatusChip status={a.status} />
                    </td>
                    <td>{a.location}</td>
                    <td>{t(a.typeKey)}</td>
                    <td>{a.assignedTo ?? t('assets.unassigned')}</td>
                  </tr>
                );
              })
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
          code={selected.tag}
          title={selected.name}
          chips={<StatusChip status={selected.status} />}
          validationMessage={validation}
          activities={activities}
          history={activities.filter((a) => a.kind === 'field' || a.kind === 'status')}
          related={related}
          relatedEmptyHint={t('module.relatedEmptyHint')}
          relatedEmptyAction={{
            label: t('module.relatedEmptyCta'),
            href: '/cmdb',
          }}
          overview={
            <dl className="module-detail-dl">
              <div>
                <dt>{t('assets.colStatus')}</dt>
                <dd>
                  <StatusChip status={selected.status} />
                </dd>
              </div>
              <div>
                <dt>{t('assets.colType')}</dt>
                <dd>{t(selected.typeKey)}</dd>
              </div>
              <div>
                <dt>{t('assets.colAssignee')}</dt>
                <dd>
                  {selected.status === 'retired' ? (
                    selected.assignedTo ?? t('assets.unassigned')
                  ) : (
                    <input
                      className="module-inline-input"
                      value={assignName}
                      onChange={(e) => setAssignName(e.target.value)}
                      placeholder={t('assets.unassigned')}
                      aria-label={t('assets.colAssignee')}
                    />
                  )}
                </dd>
              </div>
              <div>
                <dt>{t('assets.colLocation')}</dt>
                <dd>{selected.location}</dd>
              </div>
              <div>
                <dt>{t('assets.purchased')}</dt>
                <dd>{formatDate(selected.purchasedAt, locale)}</dd>
              </div>
              {selected.serial && (
                <div>
                  <dt>{t('assets.serial')}</dt>
                  <dd className="mono">{selected.serial}</dd>
                </div>
              )}
              {selected.model && (
                <div>
                  <dt>{t('assets.model')}</dt>
                  <dd>{selected.model}</dd>
                </div>
              )}
              {selected.vendor && (
                <div>
                  <dt>{t('assets.vendor')}</dt>
                  <dd>{selected.vendor}</dd>
                </div>
              )}
              {selected.costCenter && (
                <div>
                  <dt>{t('assets.costCenter')}</dt>
                  <dd>{selected.costCenter}</dd>
                </div>
              )}
              {selected.notes && (
                <div className="module-detail-dl__wide">
                  <dt>{t('assets.notes')}</dt>
                  <dd>{selected.notes}</dd>
                </div>
              )}
            </dl>
          }
          actions={
            transitions.length > 0 ? (
              <>
                {transitions.map((s) => (
                  <Button
                    key={s}
                    size="sm"
                    variant={s === 'retired' ? 'secondary' : 'primary'}
                    onClick={() => void runTransition(s)}
                  >
                    {t(`assets.actions.to_${s}`)}
                  </Button>
                ))}
              </>
            ) : (
              <span className="muted">{t('module.noTransitions')}</span>
            )
          }
        />
      )}

      <CreateAssetModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(a) => {
          setCreateOpen(false);
          success(t('assets.created', { tag: a.tag }));
          setSelectedId(a.id);
          reload();
        }}
      />
    </section>
  );
}

function CreateAssetModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (a: Asset) => void;
}) {
  const t = useT();
  const [tag, setTag] = useState('');
  const [name, setName] = useState('');
  const [typeKey, setTypeKey] = useState('assets.types.laptop');
  const [location, setLocation] = useState('');
  const [serial, setSerial] = useState('');
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setTag('');
      setName('');
      setTypeKey('assets.types.laptop');
      setLocation('');
      setSerial('');
      setNotes('');
      setErrors({});
      setSubmitting(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!tag.trim()) next.tag = t('assets.validation.tag');
    if (!name.trim()) next.name = t('assets.validation.name');
    if (!location.trim()) next.location = t('assets.validation.location');
    setErrors(next);
    if (Object.keys(next).length) return;
    setSubmitting(true);
    try {
      const created = await createAsset({
        tag: tag.trim(),
        name: name.trim(),
        typeKey,
        location: location.trim(),
        serial: serial.trim() || undefined,
        notes: notes.trim() || undefined,
        status: 'stock',
      });
      onCreated(created);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={t('assets.addAsset')} labelledBy="create-asset-title">
      <form className="module-create-form" onSubmit={(e) => void submit(e)}>
        <Input
          label={t('assets.colTag')}
          value={tag}
          onChange={(e) => setTag(e.target.value)}
          error={errors.tag}
          required
          autoFocus
        />
        <Input
          label={t('assets.colName')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={errors.name}
          required
        />
        <Select
          label={t('assets.colType')}
          value={typeKey}
          onChange={(e) => setTypeKey(e.target.value)}
          options={[
            { value: 'assets.types.laptop', label: t('assets.types.laptop') },
            { value: 'assets.types.monitor', label: t('assets.types.monitor') },
            { value: 'assets.types.phone', label: t('assets.types.phone') },
            {
              value: 'assets.types.peripheral',
              label: t('assets.types.peripheral'),
            },
          ]}
        />
        <Input
          label={t('assets.colLocation')}
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          error={errors.location}
          required
        />
        <Input
          label={t('assets.serial')}
          value={serial}
          onChange={(e) => setSerial(e.target.value)}
        />
        <Textarea
          label={t('assets.notes')}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
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

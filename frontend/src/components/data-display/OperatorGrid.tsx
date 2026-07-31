import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowDown,
  ArrowUp,
  ChevronUp,
} from 'lucide-react';
import type { Priority, WorkItem } from '@/types';
import { useT } from '@/i18n';
import { useDensity } from '@/hooks/useDensity';
import { useToast } from '@/hooks/useToast';
import { bulkAssignWorkItems, bulkSetPriority } from '@/api';
import { Avatar, Button, EmptyState, SkeletonRows } from '@/components/ui';
import { PriorityBadge } from './PriorityBadge';
import { SlaMiniBar } from './SlaMiniBar';
import { formatRelative } from '@/lib/format';

export type SortKey = 'priority' | 'sla' | 'updated' | 'number';
export type SortDir = 'asc' | 'desc';

const PRIORITY_RANK: Record<Priority, number> = {
  critical: 0,
  high: 1,
  medium: 2,
  low: 3,
};

const SLA_RANK: Record<string, number> = {
  breached: 0,
  at_risk: 1,
  on_track: 2,
  met: 3,
};

interface OperatorGridProps {
  items: WorkItem[];
  loading?: boolean;
  emptyTitle: string;
  emptyHint?: string;
  emptyActionLabel?: string;
  onEmptyAction?: () => void;
  onBulkAssign?: (ids: string[]) => void;
  onBulkPriority?: (ids: string[], priority: Priority) => void;
  showKeyboardHint?: boolean;
  /** Show queue name column (Queues / Overview) */
  showQueue?: boolean;
  /** Hide bulk toolbar (Overview mini panel) */
  compact?: boolean;
  /** Max rows to render (Overview) */
  limit?: number;
}

export function OperatorGrid({
  items,
  loading,
  emptyTitle,
  emptyHint,
  emptyActionLabel,
  onEmptyAction,
  onBulkAssign,
  onBulkPriority,
  showKeyboardHint = true,
  showQueue = false,
  compact = false,
  limit,
}: OperatorGridProps) {
  const t = useT();
  const navigate = useNavigate();
  const { isCompact } = useDensity();
  const { success } = useToast();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [sortKey, setSortKey] = useState<SortKey>('sla');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [focusIndex, setFocusIndex] = useState(-1);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setSelected((prev) => {
      const ids = new Set(items.map((i) => i.id));
      const next = new Set([...prev].filter((id) => ids.has(id)));
      return next.size === prev.size ? prev : next;
    });
  }, [items]);

  const sorted = useMemo(() => {
    const list = [...items];
    const dir = sortDir === 'asc' ? 1 : -1;
    list.sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'priority') {
        cmp = PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority];
      } else if (sortKey === 'sla') {
        cmp = (SLA_RANK[a.slaState] ?? 9) - (SLA_RANK[b.slaState] ?? 9);
        if (cmp === 0) cmp = a.slaTarget.localeCompare(b.slaTarget);
      } else if (sortKey === 'updated') {
        cmp = a.updatedAt.localeCompare(b.updatedAt);
      } else {
        cmp = a.number.localeCompare(b.number);
      }
      return cmp * dir;
    });
    return typeof limit === 'number' ? list.slice(0, limit) : list;
  }, [items, sortKey, sortDir, limit]);

  const allSelected = sorted.length > 0 && selected.size === sorted.length;
  const someSelected = selected.size > 0 && !allSelected;

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir(key === 'updated' ? 'desc' : 'asc');
    }
  };

  const toggleAll = () => {
    if (allSelected) setSelected(new Set());
    else setSelected(new Set(sorted.map((i) => i.id)));
  };

  const toggleOne = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleBulkAssign = async () => {
    const ids = [...selected];
    if (onBulkAssign) {
      onBulkAssign(ids);
    } else {
      await bulkAssignWorkItems(ids);
    }
    success(t('grid.bulkAssigned', { n: ids.length }));
    setSelected(new Set());
  };

  const handleBulkPriority = async (p: Priority) => {
    const ids = [...selected];
    if (onBulkPriority) {
      onBulkPriority(ids, p);
    } else {
      await bulkSetPriority(ids, p);
    }
    success(t('grid.bulkPriorityChanged', { n: ids.length, p: t(`priority.${p}`) }));
    setSelected(new Set());
  };

  const openItem = useCallback(
    (id: string) => navigate(`/work-items/${id}`),
    [navigate],
  );

  const onListKeyDown = (e: ReactKeyboardEvent) => {
    if (sorted.length === 0) return;
    const key = e.key.toLowerCase();
    if (e.key === 'ArrowDown' || key === 'j') {
      e.preventDefault();
      setFocusIndex((i) => Math.min(i < 0 ? 0 : i + 1, sorted.length - 1));
    } else if (e.key === 'ArrowUp' || key === 'k') {
      e.preventDefault();
      setFocusIndex((i) => Math.max(i < 0 ? 0 : i - 1, 0));
    } else if (e.key === 'Enter' && focusIndex >= 0) {
      e.preventDefault();
      openItem(sorted[focusIndex].id);
    } else if (e.key === ' ' && focusIndex >= 0) {
      e.preventDefault();
      toggleOne(sorted[focusIndex].id);
    } else if (key === 'x' && focusIndex >= 0) {
      e.preventDefault();
      toggleOne(sorted[focusIndex].id);
    } else if (e.key === 'Escape') {
      setFocusIndex(-1);
      setSelected(new Set());
    } else if (key === 'a' && (e.metaKey || e.ctrlKey)) {
      e.preventDefault();
      toggleAll();
    }
  };

  useEffect(() => {
    if (focusIndex < 0) return;
    const el = listRef.current?.querySelector<HTMLElement>(
      `[data-row-index="${focusIndex}"]`,
    );
    el?.scrollIntoView({ block: 'nearest' });
  }, [focusIndex]);

  const ariaSortFor = (col: SortKey): 'ascending' | 'descending' | 'none' => {
    if (sortKey !== col) return 'none';
    return sortDir === 'asc' ? 'ascending' : 'descending';
  };

  const SortIcon = ({ col }: { col: SortKey }) => {
    if (sortKey !== col) return <ChevronUp size={12} className="sort-icon sort-icon--idle" />;
    return sortDir === 'asc' ? (
      <ArrowUp size={12} className="sort-icon" />
    ) : (
      <ArrowDown size={12} className="sort-icon" />
    );
  };

  return (
    <div
      className={`operator-grid${isCompact || compact ? ' is-compact' : ''}${
        showQueue ? ' operator-grid--with-queue' : ''
      }`}
    >
      {!compact && selected.size > 0 && (
        <div className="bulk-bar" role="toolbar" aria-label={t('grid.bulkActions')}>
          <span className="bulk-bar__count">
            {t('grid.selected', { n: selected.size })}
          </span>
          <Button variant="secondary" size="sm" onClick={handleBulkAssign}>
            {t('grid.assignToMe')}
          </Button>
          <div className="bulk-bar__priority">
            <span>{t('grid.changePriority')}</span>
            {(['critical', 'high', 'medium', 'low'] as Priority[]).map((p) => (
              <button
                key={p}
                type="button"
                className={`chip chip--toggle bulk-prio bulk-prio--${p}`}
                onClick={() => handleBulkPriority(p)}
              >
                {t(`priority.${p}`)}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="text-link bulk-bar__clear"
            onClick={() => setSelected(new Set())}
          >
            {t('grid.clearSelection')}
          </button>
        </div>
      )}

      {showKeyboardHint && !compact && !loading && sorted.length > 0 && (
        <div className="grid-kbd-hint" aria-hidden>
          <kbd>↑</kbd>
          <kbd>↓</kbd>
          <span>/</span>
          <kbd>J</kbd>
          <kbd>K</kbd>
          <span>{t('grid.kbdNav')}</span>
          <kbd>Enter</kbd>
          <span>{t('grid.kbdOpen')}</span>
          <kbd>X</kbd>
          <span>{t('grid.kbdSelect')}</span>
        </div>
      )}

      <div className="panel panel--flush operator-grid__panel">
        <div
          role="table"
          aria-label={t('grid.listLabel')}
          aria-rowcount={sorted.length + 1}
        >
          <div role="rowgroup">
            <div
              className={`table-head table-head--sticky table-head--grid${
                showQueue ? ' table-head--grid-queue' : ''
              }`}
              role="row"
            >
              <div role="columnheader" className="grid-check">
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
              </div>
              <div role="columnheader" aria-sort={ariaSortFor('number')}>
                <button type="button" className="th-sort" onClick={() => toggleSort('number')}>
                  {t('overview.colRequest')}
                  <SortIcon col="number" />
                </button>
              </div>
              {showQueue && (
                <div role="columnheader">
                  <span>{t('overview.colQueue')}</span>
                </div>
              )}
              <div role="columnheader" aria-sort={ariaSortFor('priority')}>
                <button type="button" className="th-sort" onClick={() => toggleSort('priority')}>
                  {t('overview.colPriority')}
                  <SortIcon col="priority" />
                </button>
              </div>
              <div role="columnheader">
                <span>{t('overview.colAssignee')}</span>
              </div>
              <div role="columnheader" aria-sort={ariaSortFor('sla')}>
                <button type="button" className="th-sort" onClick={() => toggleSort('sla')}>
                  {t('overview.colSla')}
                  <SortIcon col="sla" />
                </button>
              </div>
              <div role="columnheader" aria-sort={ariaSortFor('updated')}>
                <button type="button" className="th-sort" onClick={() => toggleSort('updated')}>
                  {t('overview.colUpdated')}
                  <SortIcon col="updated" />
                </button>
              </div>
            </div>
          </div>

          {loading ? (
            <SkeletonRows rows={compact ? 4 : 6} />
          ) : sorted.length === 0 ? (
            <EmptyState
              title={emptyTitle}
              description={emptyHint}
              actionLabel={emptyActionLabel}
              onAction={onEmptyAction}
            />
          ) : (
            <div
              className="wi-list"
              ref={listRef}
              tabIndex={0}
              role="rowgroup"
              onKeyDown={onListKeyDown}
            >
              {sorted.map((item, index) => {
                const isSelected = selected.has(item.id);
                const isFocused = focusIndex === index;
                return (
                  <div
                    key={item.id}
                    role="row"
                    aria-selected={isSelected}
                    aria-rowindex={index + 2}
                    data-row-index={index}
                    className={`wi-row wi-row--grid${showQueue ? ' wi-row--grid-queue' : ''}${
                      isCompact || compact ? ' wi-row--dense' : ''
                    }${isSelected ? ' is-selected' : ''}${isFocused ? ' is-focused' : ''}${
                      item.slaState === 'breached' ? ' wi-row--breach' : ''
                    }${item.slaState === 'at_risk' ? ' wi-row--risk' : ''}`}
                    onClick={() => openItem(item.id)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') openItem(item.id);
                    }}
                    tabIndex={isFocused ? 0 : -1}
                  >
                    <div role="cell" className="grid-check">
                      <label
                        onClick={(e) => e.stopPropagation()}
                        onKeyDown={(e) => e.stopPropagation()}
                      >
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleOne(item.id)}
                          aria-label={t('grid.selectRow', { n: item.number })}
                        />
                      </label>
                    </div>
                    <div role="cell" className="wi-row__ticket">
                      <b>{item.number}</b>
                      <span>{item.title}</span>
                      <small>
                        {t(`workItemType.${item.type}`)}
                        {!showQueue && item.queue ? ` · ${item.queue}` : ''}
                      </small>
                    </div>
                    {showQueue && (
                      <div role="cell" className="wi-row__queue">
                        <span className="queue-pill">{item.queue ?? '—'}</span>
                      </div>
                    )}
                    <div role="cell">
                      <PriorityBadge priority={item.priority} />
                    </div>
                    <div role="cell" className="wi-row__person">
                      {item.assignee ? (
                        <>
                          <Avatar initials={item.assignee.initials} size="sm" />
                          <span>{item.assignee.name}</span>
                        </>
                      ) : (
                        <span className="muted">{t('overview.unassigned')}</span>
                      )}
                    </div>
                    <div role="cell">
                      <SlaMiniBar state={item.slaState} target={item.slaTarget} />
                    </div>
                    <div role="cell" className="muted">
                      {formatRelative(item.updatedAt, t)}
                    </div>
                    <div className="wi-row__meta-line">
                      {showQueue && item.queue && (
                        <span className="queue-pill queue-pill--sm">{item.queue}</span>
                      )}
                      <PriorityBadge priority={item.priority} />
                      <SlaMiniBar state={item.slaState} target={item.slaTarget} compact />
                      <span className="muted">{formatRelative(item.updatedAt, t)}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

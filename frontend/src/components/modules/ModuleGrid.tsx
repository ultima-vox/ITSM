import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from 'react';
import { ArrowDown, ArrowUp, ChevronUp } from 'lucide-react';
import { useT } from '@/i18n';
import { useDensity } from '@/hooks/useDensity';
import { EmptyState, ErrorState, SkeletonRows } from '@/components/ui';
import { ModuleBulkBar, ModuleKbdHint } from './ModuleBulkBar';

export type ModuleGridSortDir = 'asc' | 'desc';

export interface ModuleGridColumn<T> {
  id: string;
  header: ReactNode;
  /** When set, header is sortable with aria-sort */
  sortKey?: string;
  render: (row: T) => ReactNode;
  /** CSS grid track (e.g. `minmax(120px, 1.2fr)` or `90px`) */
  width?: string;
  className?: string;
}

export interface ModuleGridProps<T> {
  rows: T[];
  columns: ModuleGridColumn<T>[];
  getRowId: (row: T) => string;
  /** Used for aria-label on row checkboxes */
  getRowLabel: (row: T) => string;
  ariaLabel: string;

  loading?: boolean;

  /** Controlled sort chrome — parent owns row order */
  sortKey?: string | null;
  sortDir?: ModuleGridSortDir;
  onSort?: (key: string) => void;

  selectedIds: Set<string>;
  onSelectionChange: (ids: Set<string>) => void;

  onRowOpen: (row: T) => void;
  /** Highlight the row whose detail drawer is open */
  activeRowId?: string | null;

  emptyTitle: string;
  emptyHint?: string;
  emptyActionLabel?: string;
  onEmptyAction?: () => void;

  /** Override default empty / loading / error bodies */
  emptySlot?: ReactNode;
  loadingSlot?: ReactNode;
  errorSlot?: ReactNode;
  /** When true and not loading, show errorSlot or default ErrorState */
  error?: boolean;
  onRetry?: () => void;

  /** Sticky bulk bar (ModuleBulkBar) when selection > 0 */
  onBulkAssign?: () => void;
  bulkActions?: ReactNode;
  bulkStatusLabel?: string;
  showBulkStatusLabel?: boolean;

  showKeyboardHint?: boolean;
  className?: string;
}

/**
 * OperatorGrid-class list for secondary modules (Assets / Problems / Changes).
 * Sticky header, bulk checkboxes, sticky bulk bar, aria-sort, J/K · ↑↓ · Space · Enter · Ctrl+A · Esc.
 */
export function ModuleGrid<T>({
  rows,
  columns,
  getRowId,
  getRowLabel,
  ariaLabel,
  loading,
  sortKey = null,
  sortDir = 'asc',
  onSort,
  selectedIds,
  onSelectionChange,
  onRowOpen,
  activeRowId = null,
  emptyTitle,
  emptyHint,
  emptyActionLabel,
  onEmptyAction,
  emptySlot,
  loadingSlot,
  errorSlot,
  error,
  onRetry,
  onBulkAssign,
  bulkActions,
  bulkStatusLabel,
  showBulkStatusLabel = true,
  showKeyboardHint = true,
  className,
}: ModuleGridProps<T>) {
  const t = useT();
  const { isCompact } = useDensity();
  const [focusIndex, setFocusIndex] = useState(-1);
  const listRef = useRef<HTMLDivElement>(null);

  // Drop selection ids that left the filtered set
  useEffect(() => {
    const ids = new Set(rows.map(getRowId));
    const next = new Set([...selectedIds].filter((id) => ids.has(id)));
    if (next.size !== selectedIds.size) onSelectionChange(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- prune only when rows change
  }, [rows]);

  const allSelected = rows.length > 0 && selectedIds.size === rows.length;
  const someSelected = selectedIds.size > 0 && !allSelected;

  const toggleAll = useCallback(() => {
    if (allSelected) onSelectionChange(new Set());
    else onSelectionChange(new Set(rows.map(getRowId)));
  }, [allSelected, getRowId, onSelectionChange, rows]);

  const toggleOne = useCallback(
    (id: string) => {
      const next = new Set(selectedIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      onSelectionChange(next);
    },
    [onSelectionChange, selectedIds],
  );

  const clearSelection = useCallback(() => {
    onSelectionChange(new Set());
  }, [onSelectionChange]);

  const ariaSortFor = (
    colKey: string | undefined,
  ): 'ascending' | 'descending' | 'none' | undefined => {
    if (!colKey) return undefined;
    if (sortKey !== colKey) return 'none';
    return sortDir === 'asc' ? 'ascending' : 'descending';
  };

  const onListKeyDown = (e: ReactKeyboardEvent) => {
    if (rows.length === 0) return;
    const key = e.key.toLowerCase();
    if (e.key === 'ArrowDown' || key === 'j') {
      e.preventDefault();
      setFocusIndex((i) => Math.min(i < 0 ? 0 : i + 1, rows.length - 1));
    } else if (e.key === 'ArrowUp' || key === 'k') {
      e.preventDefault();
      setFocusIndex((i) => Math.max(i < 0 ? 0 : i - 1, 0));
    } else if (e.key === 'Enter' && focusIndex >= 0) {
      e.preventDefault();
      onRowOpen(rows[focusIndex]);
    } else if (e.key === ' ' && focusIndex >= 0) {
      e.preventDefault();
      toggleOne(getRowId(rows[focusIndex]));
    } else if ((e.metaKey || e.ctrlKey) && key === 'a') {
      e.preventDefault();
      toggleAll();
    } else if (e.key === 'Escape') {
      setFocusIndex(-1);
      clearSelection();
    }
  };

  useEffect(() => {
    if (focusIndex < 0) return;
    const el = listRef.current?.querySelector<HTMLElement>(
      `[data-row-index="${focusIndex}"]`,
    );
    el?.scrollIntoView({ block: 'nearest' });
  }, [focusIndex]);

  // Clamp focus when list shrinks
  useEffect(() => {
    if (focusIndex >= rows.length) {
      setFocusIndex(rows.length > 0 ? rows.length - 1 : -1);
    }
  }, [rows.length, focusIndex]);

  const gridTemplate = useMemo(() => {
    const tracks = [
      '36px',
      ...columns.map((c) => c.width ?? 'minmax(80px, 1fr)'),
    ];
    return tracks.join(' ');
  }, [columns]);

  const gridStyle = {
    '--module-grid-cols': gridTemplate,
  } as CSSProperties;

  const SortIcon = ({ col }: { col: string }) => {
    if (sortKey !== col)
      return <ChevronUp size={12} className="sort-icon sort-icon--idle" />;
    return sortDir === 'asc' ? (
      <ArrowUp size={12} className="sort-icon" />
    ) : (
      <ArrowDown size={12} className="sort-icon" />
    );
  };

  const showError = Boolean(error) && !loading;
  const showEmpty = !loading && !showError && rows.length === 0;
  const showRows = !loading && !showError && rows.length > 0;

  return (
    <div
      className={`module-grid${isCompact ? ' is-compact' : ''}${
        className ? ` ${className}` : ''
      }`}
      style={gridStyle}
    >
      {onBulkAssign && (
        <ModuleBulkBar
          selectedCount={selectedIds.size}
          onAssign={onBulkAssign}
          onClear={clearSelection}
          statusLabel={bulkStatusLabel}
          showStatusLabel={showBulkStatusLabel}
        >
          {bulkActions}
        </ModuleBulkBar>
      )}

      {showKeyboardHint && showRows && <ModuleKbdHint selectKey="Space" />}

      <div className="panel panel--flush module-grid__panel">
        <div
          role="table"
          aria-label={ariaLabel}
          aria-rowcount={showRows ? rows.length + 1 : undefined}
        >
          <div role="rowgroup">
            <div
              className="table-head table-head--sticky table-head--module-grid"
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
                    disabled={rows.length === 0 || loading}
                  />
                </label>
              </div>
              {columns.map((col) => (
                <div
                  key={col.id}
                  role="columnheader"
                  aria-sort={ariaSortFor(col.sortKey)}
                  className={col.className}
                >
                  {col.sortKey && onSort ? (
                    <button
                      type="button"
                      className="th-sort"
                      onClick={() => onSort(col.sortKey!)}
                    >
                      {col.header}
                      <SortIcon col={col.sortKey} />
                    </button>
                  ) : (
                    <span>{col.header}</span>
                  )}
                </div>
              ))}
            </div>
          </div>

          {loading ? (
            loadingSlot ?? <SkeletonRows rows={5} />
          ) : showError ? (
            errorSlot ?? <ErrorState onRetry={onRetry} />
          ) : showEmpty ? (
            emptySlot ?? (
              <EmptyState
                title={emptyTitle}
                description={emptyHint}
                actionLabel={emptyActionLabel}
                onAction={onEmptyAction}
              />
            )
          ) : (
            <div
              className="module-grid__list wi-list"
              ref={listRef}
              tabIndex={0}
              role="rowgroup"
              onKeyDown={onListKeyDown}
            >
              {rows.map((row, index) => {
                const id = getRowId(row);
                const isChecked = selectedIds.has(id);
                const isFocused = focusIndex === index;
                const isActive = activeRowId === id;
                return (
                  <div
                    key={id}
                    role="row"
                    aria-selected={isChecked}
                    aria-rowindex={index + 2}
                    data-row-index={index}
                    className={[
                      'module-grid__row',
                      'wi-row',
                      'wi-row--module-grid',
                      isCompact ? 'wi-row--dense' : '',
                      isChecked || isActive ? 'is-selected' : '',
                      isFocused ? 'is-focused' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                    onClick={() => onRowOpen(row)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        onRowOpen(row);
                      }
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
                          checked={isChecked}
                          onChange={() => toggleOne(id)}
                          aria-label={t('grid.selectRow', {
                            n: getRowLabel(row),
                          })}
                        />
                      </label>
                    </div>
                    {columns.map((col) => (
                      <div
                        key={col.id}
                        role="cell"
                        className={col.className}
                      >
                        {col.render(row)}
                      </div>
                    ))}
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

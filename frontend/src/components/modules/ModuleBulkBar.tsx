import type { ReactNode } from 'react';
import { useT } from '@/i18n';
import { Button } from '@/components/ui';

export interface ModuleBulkBarProps {
  selectedCount: number;
  onAssign: () => void;
  onClear: () => void;
  /** Status / secondary action chips rendered after assign */
  children?: ReactNode;
  /** Override status-group label (default module.bulk.changeStatus) */
  statusLabel?: string;
  /** When false, omit the status label wrapper (children stand alone) */
  showStatusLabel?: boolean;
}

/**
 * Shared sticky bulk toolbar for secondary module tables
 * (Assets / Problems / Changes) — closer to OperatorGrid bulk craft.
 */
export function ModuleBulkBar({
  selectedCount,
  onAssign,
  onClear,
  children,
  statusLabel,
  showStatusLabel = true,
}: ModuleBulkBarProps) {
  const t = useT();
  if (selectedCount <= 0) return null;

  return (
    <div className="bulk-bar" role="toolbar" aria-label={t('grid.bulkActions')}>
      <span className="bulk-bar__count">
        {t('grid.selected', { n: selectedCount })}
      </span>
      <Button variant="secondary" size="sm" onClick={onAssign}>
        {t('grid.assignToMe')}
      </Button>
      {children != null && (
        <div className="bulk-bar__priority">
          {showStatusLabel && (
            <span>{statusLabel ?? t('module.bulk.changeStatus')}</span>
          )}
          {children}
        </div>
      )}
      <button
        type="button"
        className="text-link bulk-bar__clear"
        onClick={onClear}
      >
        {t('grid.clearSelection')}
      </button>
    </div>
  );
}

export interface ModuleKbdHintProps {
  /** Select key shown in hint — secondary tables use Space; OperatorGrid uses X */
  selectKey?: 'Space' | 'X';
  /** Show Ctrl+A select-all hint (secondary module tables) */
  showSelectAll?: boolean;
}

/** Visible keyboard hint strip for module list tables. */
export function ModuleKbdHint({
  selectKey = 'Space',
  showSelectAll = true,
}: ModuleKbdHintProps) {
  const t = useT();
  return (
    <div className="grid-kbd-hint" aria-hidden>
      <kbd>↑</kbd>
      <kbd>↓</kbd>
      <span>/</span>
      <kbd>J</kbd>
      <kbd>K</kbd>
      <span>{t('grid.kbdNav')}</span>
      <kbd>Enter</kbd>
      <span>{t('grid.kbdOpen')}</span>
      <kbd>{selectKey}</kbd>
      <span>{t('grid.kbdSelect')}</span>
      {showSelectAll && (
        <>
          <kbd>Ctrl</kbd>
          <kbd>A</kbd>
          <span>{t('grid.selectAll')}</span>
        </>
      )}
      <kbd>Esc</kbd>
      <span>{t('grid.kbdClear')}</span>
    </div>
  );
}

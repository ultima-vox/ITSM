import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  ArrowRightLeft,
  Clock3,
  MessageSquare,
  Pencil,
  Settings2,
  X,
} from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useI18n, useT } from '@/i18n';
import { Button, EmptyState, Tabs } from '@/components/ui';
import type { ModuleActivity } from '@/types';
import { formatDateTime, formatRelative } from '@/lib/format';

export type ModuleDetailTab = 'overview' | 'activity' | 'related' | 'history';

export interface ModuleRelatedItem {
  id: string;
  label: string;
  meta?: string;
  href?: string;
}

interface ModuleDetailDrawerProps {
  open: boolean;
  onClose: () => void;
  /** Primary number / tag shown as eyebrow */
  code: string;
  title: string;
  chips?: ReactNode;
  /** Workflow action buttons rendered under tabs on overview */
  actions?: ReactNode;
  overview: ReactNode;
  activities?: ModuleActivity[];
  related?: ModuleRelatedItem[];
  /** History entries — reuses ModuleActivity shape */
  history?: ModuleActivity[];
  validationMessage?: string | null;
  defaultTab?: ModuleDetailTab;
  /** Empty related-tab CTA */
  relatedEmptyAction?: {
    label: string;
    onClick?: () => void;
    href?: string;
  };
  relatedEmptyHint?: string;
}

export function ModuleDetailDrawer({
  open,
  onClose,
  code,
  title,
  chips,
  actions,
  overview,
  activities = [],
  related = [],
  history = [],
  validationMessage,
  defaultTab = 'overview',
  relatedEmptyAction,
  relatedEmptyHint,
}: ModuleDetailDrawerProps) {
  const t = useT();
  const ref = useRef<HTMLElement>(null);
  const [tab, setTab] = useState<ModuleDetailTab>(defaultTab);
  useFocusTrap(ref, open);

  useEffect(() => {
    if (open) setTab(defaultTab);
  }, [open, code, defaultTab]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  const tabs = [
    { id: 'overview', label: t('module.tabs.overview') },
    {
      id: 'activity',
      label: t('module.tabs.activity'),
      count: activities.length || undefined,
    },
    {
      id: 'related',
      label: t('module.tabs.related'),
      count: related.length || undefined,
    },
    {
      id: 'history',
      label: t('module.tabs.history'),
      count: history.length || undefined,
    },
  ];

  return (
    <div
      className="drawer-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <aside
        ref={ref}
        className="service-drawer module-detail-drawer module-detail-drawer--full"
        role="dialog"
        aria-modal="true"
        aria-labelledby="module-detail-title"
      >
        <div className="module-detail-drawer__chrome">
          <div className="service-drawer__head">
            <p className="eyebrow mono accent">{code}</p>
            <button
              type="button"
              className="icon-btn"
              aria-label={t('app.close')}
              onClick={onClose}
            >
              <X size={18} />
            </button>
          </div>
          <h2 id="module-detail-title">{title}</h2>
          {chips && <div className="module-detail-chips">{chips}</div>}

          <Tabs
            items={tabs}
            value={tab}
            onChange={(id) => setTab(id as ModuleDetailTab)}
            className="module-detail-tabs"
          />
        </div>

        <div className="module-detail-drawer__body">
          {validationMessage && (
            <div className="module-validation" role="alert">
              {validationMessage}
            </div>
          )}

          {tab === 'overview' && (
            <div className="module-detail-panel">
              {overview}
              {actions && (
                <div className="module-workflow" role="group" aria-label={t('module.workflow')}>
                  <p className="module-workflow__label">{t('module.workflow')}</p>
                  <div className="module-workflow__actions">{actions}</div>
                </div>
              )}
            </div>
          )}

          {tab === 'activity' && (
            <ActivityList
              items={activities}
              empty={t('module.activityEmpty')}
              mode="activity"
            />
          )}

          {tab === 'related' && (
            <RelatedList
              items={related}
              empty={t('module.relatedEmpty')}
              emptyHint={relatedEmptyHint}
              emptyAction={relatedEmptyAction}
            />
          )}

          {tab === 'history' && (
            <ActivityList
              items={history}
              empty={t('module.historyEmpty')}
              mode="history"
            />
          )}
        </div>

        <div className="service-drawer__actions module-detail-drawer__footer">
          <Button variant="secondary" fullWidth onClick={onClose}>
            {t('app.close')}
          </Button>
        </div>
      </aside>
    </div>
  );
}

function activityIcon(kind: ModuleActivity['kind']) {
  switch (kind) {
    case 'status':
      return <ArrowRightLeft size={14} aria-hidden />;
    case 'field':
      return <Pencil size={14} aria-hidden />;
    case 'comment':
      return <MessageSquare size={14} aria-hidden />;
    case 'system':
    default:
      return <Settings2 size={14} aria-hidden />;
  }
}

function ActivityList({
  items,
  empty,
  mode,
}: {
  items: ModuleActivity[];
  empty: string;
  mode: 'activity' | 'history';
}) {
  const t = useT();
  const { locale } = useI18n();

  const ordered = useMemo(() => {
    const copy = [...items];
    // Activity: newest first; History: chronological (oldest → newest)
    copy.sort((a, b) =>
      mode === 'history'
        ? a.at.localeCompare(b.at)
        : b.at.localeCompare(a.at),
    );
    return copy;
  }, [items, mode]);

  if (ordered.length === 0) {
    return <p className="module-tab-empty">{empty}</p>;
  }

  return (
    <ol
      className={`module-activity-list${mode === 'history' ? ' module-activity-list--timeline' : ''}`}
    >
      {ordered.map((a, index) => (
        <li
          key={a.id}
          className={`module-activity-item module-activity-item--${a.kind}`}
        >
          <span
            className={`module-activity-item__icon module-activity-item__icon--${a.kind}`}
            aria-hidden
          >
            {activityIcon(a.kind)}
          </span>
          <div className="module-activity-item__body">
            <p>
              <span className="module-activity-item__kind">{t(a.textKey)}</span>
              {a.detail ? (
                <span className="module-activity-item__detail"> — {a.detail}</span>
              ) : null}
            </p>
            <div className="module-activity-item__meta">
              <span className="module-activity-item__actor">{a.actor.name}</span>
              <time dateTime={a.at} title={formatDateTime(a.at, locale)}>
                <Clock3 size={12} aria-hidden />
                {mode === 'history'
                  ? formatDateTime(a.at, locale)
                  : formatRelative(a.at, t)}
              </time>
            </div>
          </div>
          {mode === 'history' && index < ordered.length - 1 && (
            <span className="module-activity-item__rail" aria-hidden />
          )}
        </li>
      ))}
    </ol>
  );
}

function RelatedList({
  items,
  empty,
  emptyHint,
  emptyAction,
}: {
  items: ModuleRelatedItem[];
  empty: string;
  emptyHint?: string;
  emptyAction?: {
    label: string;
    onClick?: () => void;
    href?: string;
  };
}) {
  const navigate = useNavigate();

  if (items.length === 0) {
    if (emptyAction) {
      return (
        <EmptyState
          title={empty}
          description={emptyHint}
          actionLabel={emptyAction.label}
          onAction={
            emptyAction.onClick ??
            (emptyAction.href
              ? () => navigate(emptyAction.href!)
              : undefined)
          }
        />
      );
    }
    return <p className="module-tab-empty">{empty}</p>;
  }
  return (
    <ul className="module-related-list">
      {items.map((r) => {
        const content = (
          <>
            <span className="module-related-link__label">{r.label}</span>
            {r.meta && (
              <span className="muted module-related-link__meta">{r.meta}</span>
            )}
          </>
        );
        return (
          <li key={r.id}>
            {r.href ? (
              r.href.startsWith('/') ? (
                <Link to={r.href} className="module-related-link">
                  {content}
                </Link>
              ) : (
                <a href={r.href} className="module-related-link">
                  {content}
                </a>
              )
            ) : (
              <div className="module-related-link">{content}</div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

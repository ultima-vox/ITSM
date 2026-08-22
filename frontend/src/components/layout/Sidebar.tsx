import { useEffect, useRef, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { ChevronDown, ShieldCheck } from 'lucide-react';
import { useT } from '@/i18n';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { fetchMyOpenCount } from '@/api/workItems';
import { subscribeNotifications } from '@/api/notifications';
import { ProfileMenu } from './ProfileMenu';
import type { NavItem } from './nav';

interface SidebarProps {
  open: boolean;
  onClose: () => void;
  items: readonly NavItem[];
  secondaryItems?: readonly NavItem[];
  showWorkspace?: boolean;
}

export function Brand({ to }: { to?: string }) {
  const t = useT();
  const inner = (
    <>
      <span className="brand-mark" aria-hidden>
        <span />
        <span />
        <span />
      </span>
      <b>{t('app.brand')}</b>
      <em>{t('app.brandProduct')}</em>
    </>
  );
  if (to) {
    return (
      <Link to={to} className="brand" aria-label={t('app.name')}>
        {inner}
      </Link>
    );
  }
  return <div className="brand">{inner}</div>;
}

function NavList({
  items,
  onClose,
  myWorkCount,
}: {
  items: readonly NavItem[];
  onClose: () => void;
  myWorkCount: number;
}) {
  const t = useT();
  return (
    <nav className="sidebar__nav">
      {items.map(({ to, key, icon: Icon, end, liveBadge }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) => (isActive ? 'is-active' : undefined)}
          onClick={onClose}
        >
          <Icon size={18} aria-hidden />
          <span>{t(`nav.${key}`)}</span>
          {liveBadge && myWorkCount > 0 && <i>{myWorkCount}</i>}
        </NavLink>
      ))}
    </nav>
  );
}

export function Sidebar({
  open,
  onClose,
  items,
  secondaryItems,
  showWorkspace = true,
}: SidebarProps) {
  const t = useT();
  const asideRef = useRef<HTMLElement>(null);
  useFocusTrap(asideRef, open);
  const [myWorkCount, setMyWorkCount] = useState(0);

  useEffect(() => {
    fetchMyOpenCount().then(setMyWorkCount).catch(() => setMyWorkCount(0));
    return subscribeNotifications(() => {
      fetchMyOpenCount().then(setMyWorkCount).catch(() => setMyWorkCount(0));
    });
  }, []);

  useEffect(() => {
    if (!open) return;
    const first = asideRef.current?.querySelector<HTMLElement>(
      'a, button:not([disabled])',
    );
    first?.focus();
  }, [open]);

  return (
    <>
      {open && (
        <div
          className="sidebar-overlay"
          onClick={onClose}
          aria-hidden
        />
      )}
      <aside
        ref={asideRef}
        className={`sidebar${open ? ' is-open' : ''}`}
        aria-label={t('app.primaryNav')}
        aria-hidden={false}
      >
        <Brand />

        {showWorkspace && (
          <button
            type="button"
            className="workspace"
            aria-haspopup="listbox"
            aria-label={t('app.workspace')}
          >
            <span className="workspace__icon">N</span>
            <div>
              <strong>{(import.meta.env.VITE_WORKSPACE_NAME as string | undefined) ?? 'ITSM'}</strong>
              <small>{t('app.workspace')}</small>
            </div>
            <ChevronDown size={15} className="workspace__chevron" aria-hidden />
          </button>
        )}

        <NavList items={items} onClose={onClose} myWorkCount={myWorkCount} />

        {secondaryItems && secondaryItems.length > 0 && (
          <>
            <div className="sidebar__label">{t('app.management')}</div>
            <NavList items={secondaryItems} onClose={onClose} myWorkCount={myWorkCount} />
          </>
        )}

        <div className="sidebar__bottom">
          <div className="sidebar__security">
            <ShieldCheck size={16} aria-hidden />
            <span>{t('app.secureConnection')}</span>
          </div>
          <ProfileMenu compact onDark />
        </div>
      </aside>
    </>
  );
}

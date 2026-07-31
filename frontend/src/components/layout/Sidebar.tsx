import { useEffect, useRef, useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  BookOpen,
  Boxes,
  ChevronDown,
  ClipboardList,
  Database,
  Gauge,
  GitBranch,
  Grid2X2,
  LayoutDashboard,
  Package,
  Settings,
  ShieldCheck,
  TicketCheck,
  AlertOctagon,
  Zap,
} from 'lucide-react';
import { useT } from '@/i18n';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { workspaceName } from '@/mock/data';
import { countMyOpenAssigned, subscribeWorkItems } from '@/mock/store';
import { ProfileMenu } from './ProfileMenu';

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

const primaryNav = [
  { to: '/', key: 'overview', icon: LayoutDashboard, end: true },
  { to: '/my-work', key: 'myWork', icon: TicketCheck, liveBadge: true },
  { to: '/queues', key: 'queues', icon: Grid2X2 },
  { to: '/catalog', key: 'catalog', icon: ClipboardList },
  { to: '/knowledge', key: 'knowledge', icon: BookOpen },
  { to: '/cmdb', key: 'cmdb', icon: Boxes },
  { to: '/assets', key: 'assets', icon: Package },
  { to: '/problems', key: 'problems', icon: AlertOctagon },
  { to: '/changes', key: 'changes', icon: GitBranch },
] as const;

/** Management section — Metadata + Automation for demo configurability. */
const secondaryNav = [
  { to: '/admin/metadata', key: 'metadata', icon: Database },
  { to: '/admin/automation', key: 'automation', icon: Zap },
  { to: '/settings', key: 'settings', icon: Settings },
] as const;

export function Sidebar({ open, onClose }: SidebarProps) {
  const t = useT();
  const asideRef = useRef<HTMLElement>(null);
  useFocusTrap(asideRef, open);
  const [myWorkCount, setMyWorkCount] = useState(() => countMyOpenAssigned());

  useEffect(() => {
    return subscribeWorkItems(() => {
      setMyWorkCount(countMyOpenAssigned());
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
        <div className="brand">
          <span className="brand-mark" aria-hidden>
            <span />
            <span />
            <span />
          </span>
          <b>vox</b>
          <em>ITSM</em>
        </div>

        <button
          type="button"
          className="workspace"
          aria-haspopup="listbox"
          aria-label={t('app.workspace')}
        >
          <span className="workspace__icon">N</span>
          <div>
            <strong>{workspaceName}</strong>
            <small>{t('app.workspace')}</small>
          </div>
          <ChevronDown size={15} className="workspace__chevron" aria-hidden />
        </button>

        <nav className="sidebar__nav">
          {primaryNav.map(({ to, key, icon: Icon, ...rest }) => (
            <NavLink
              key={to}
              to={to}
              end={'end' in rest ? rest.end : false}
              className={({ isActive }) => (isActive ? 'is-active' : undefined)}
              onClick={onClose}
            >
              <Icon size={18} aria-hidden />
              <span>{t(`nav.${key}`)}</span>
              {'liveBadge' in rest && rest.liveBadge && myWorkCount > 0 && (
                <i>{myWorkCount}</i>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__label">{t('app.management')}</div>
        <nav className="sidebar__nav">
          <NavLink
            to="/reports"
            className={({ isActive }) => (isActive ? 'is-active' : undefined)}
            onClick={onClose}
          >
            <Gauge size={18} aria-hidden />
            <span>{t('nav.reports')}</span>
          </NavLink>
          {secondaryNav.map(({ to, key, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => (isActive ? 'is-active' : undefined)}
              onClick={onClose}
            >
              <Icon size={18} aria-hidden />
              <span>{t(`nav.${key}`)}</span>
            </NavLink>
          ))}
        </nav>

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

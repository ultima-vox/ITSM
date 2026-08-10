import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ChevronDown,
  Contrast,
  LogIn,
  LogOut,
  Moon,
  Rows3,
  Settings,
  Sun,
  User,
} from 'lucide-react';
import { useAuth } from '@/auth';
import { useT } from '@/i18n';
import { useDensity } from '@/hooks/useDensity';
import { useTheme } from '@/hooks/useTheme';
import { Avatar } from '@/components/ui';
import { currentUser } from '@/mock/data';

interface ProfileMenuProps {
  compact?: boolean;
  onDark?: boolean;
}

export function ProfileMenu({ compact, onDark }: ProfileMenuProps) {
  const t = useT();
  const navigate = useNavigate();
  const { density, toggleDensity } = useDensity();
  const { theme, cycleTheme } = useTheme();
  const { oidcEnabled, isAuthenticated, user, login, logout } = useAuth();
  const ThemeIcon =
    theme === 'dark' ? Moon : theme === 'high-contrast' ? Contrast : Sun;
  const themeLabel =
    theme === 'dark'
      ? t('settings.theme_dark')
      : theme === 'high-contrast'
        ? t('settings.theme_highContrast')
        : t('settings.theme_light');
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const displayName = isAuthenticated && user ? user.name : currentUser.name;
  const displayEmail =
    isAuthenticated && user
      ? user.email || user.username || user.sub
      : currentUser.email;
  const displayRole =
    isAuthenticated && user?.roles?.length
      ? user.roles.filter((r) => !r.startsWith('default-') && r !== 'offline_access' && r !== 'uma_authorization')[0] ||
        currentUser.role
      : currentUser.role;
  const initials =
    isAuthenticated && user ? user.initials : currentUser.initials;

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className={`profile-menu${onDark ? ' profile-menu--dark' : ''}`} ref={ref}>
      <button
        type="button"
        className={compact ? 'sidebar__profile' : 'profile-trigger'}
        aria-label={`${displayName}, ${displayRole}`}
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => setOpen((v) => !v)}
      >
        <Avatar initials={initials} tone="me" />
        {!compact && (
          <>
            <span className="profile-trigger__meta">
              <b>{displayName}</b>
              <small>{displayRole}</small>
            </span>
            <ChevronDown size={14} aria-hidden />
          </>
        )}
        {compact && (
          <>
            <span>
              <b>{displayName}</b>
              <small>{displayRole}</small>
            </span>
            <ChevronDown size={15} aria-hidden />
          </>
        )}
      </button>

      {open && (
        <div
          className={`dropdown-panel profile-panel${onDark ? ' profile-panel--up' : ''}`}
          role="menu"
        >
          <div className="profile-panel__identity">
            <Avatar initials={initials} tone="me" size="lg" />
            <div>
              <b>{displayName}</b>
              <small>{displayEmail}</small>
            </div>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              navigate('/settings');
            }}
          >
            <User size={15} aria-hidden />
            {t('profile.viewProfile')}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              navigate('/settings');
            }}
          >
            <Settings size={15} aria-hidden />
            {t('nav.settings')}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              cycleTheme();
            }}
          >
            <ThemeIcon size={15} aria-hidden />
            {t('settings.theme')}: {themeLabel}
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              toggleDensity();
            }}
          >
            <Rows3 size={15} aria-hidden />
            {density === 'compact'
              ? t('app.densityComfortable')
              : t('app.densityCompact')}
          </button>
          <div className="dropdown-divider" />
          {oidcEnabled && !isAuthenticated && (
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setOpen(false);
                void login();
              }}
            >
              <LogIn size={15} aria-hidden />
              {t('auth.signIn')}
            </button>
          )}
          {oidcEnabled && isAuthenticated && (
            <button
              type="button"
              role="menuitem"
              className="is-muted"
              onClick={() => {
                setOpen(false);
                void logout();
              }}
            >
              <LogOut size={15} aria-hidden />
              {t('auth.signOut')}
            </button>
          )}
          {!oidcEnabled && (
            <button
              type="button"
              role="menuitem"
              className="is-muted"
              onClick={() => setOpen(false)}
            >
              <LogOut size={15} aria-hidden />
              {t('profile.signOut')}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

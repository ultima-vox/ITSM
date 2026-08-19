import { useCallback, useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { LogIn } from 'lucide-react';
import { useAuth } from '@/auth';
import { useT } from '@/i18n';
import { Button } from '@/components/ui';
import { Sidebar } from './Sidebar';
import { Header, type CrumbItem } from './Header';
import { CreateWorkItemModal } from '@/components/create/CreateWorkItemModal';
import {
  CommandPalette,
  useCommandPaletteHotkey,
} from '@/components/command/CommandPalette';
import type { CreateKind } from '@/types';

const crumbMap: Record<string, string> = {
  '/': 'nav.overview',
  '/my-work': 'nav.myWork',
  '/queues': 'nav.queues',
  '/catalog': 'nav.catalog',
  '/knowledge': 'nav.knowledge',
  '/cmdb': 'nav.cmdb',
  '/assets': 'nav.assets',
  '/problems': 'nav.problems',
  '/changes': 'nav.changes',
  '/reports': 'nav.reports',
  '/settings': 'nav.settings',
  '/admin/metadata': 'nav.metadata',
  '/admin/automation': 'nav.automation',
  '/admin/workflow': 'nav.workflow',
  '/admin/sla': 'nav.sla',
  '/admin/rbac': 'nav.rbac',
  '/admin/audit': 'nav.audit',
  '/search': 'nav.search',
  '/notifications': 'nav.notifications',
};

export function AppShell() {
  const t = useT();
  const location = useLocation();
  const { needsSignIn, login, loading: authLoading } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [createKind, setCreateKind] = useState<CreateKind | null>(null);
  const [cmdOpen, setCmdOpen] = useState(false);
  const [bannerDismissed, setBannerDismissed] = useState(false);

  const openCommand = useCallback(() => setCmdOpen(true), []);
  useCommandPaletteHotkey(openCommand);

  // Close mobile drawer on route change
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  // Escape closes mobile drawer
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [menuOpen]);

  const crumbs = useMemo<CrumbItem[]>(() => {
    if (location.pathname.startsWith('/work-items/')) {
      const id = location.pathname.split('/').pop() ?? '';
      return [
        { label: t('nav.queues'), to: '/queues' },
        { label: id },
      ];
    }
    const key = crumbMap[location.pathname];
    if (!key || location.pathname === '/') {
      return [{ label: t('nav.overview') }];
    }
    return [{ label: t(key) }];
  }, [location.pathname, t]);

  const openCreate = useCallback((kind: CreateKind) => setCreateKind(kind), []);

  // Hold the app while the identity provider session is being restored, so pages do not
  // fire a burst of unauthenticated requests that resolve as 401 right before the redirect.
  if (authLoading) {
    return (
      <div className="shell shell--booting" role="status" aria-live="polite">
        <p>{t('app.loading')}</p>
      </div>
    );
  }

  return (
    <div className="shell">
      <a href="#main-content" className="skip-link">
        {t('app.skipToContent')}
      </a>
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <div className="shell__main">
        <Header
          onMenu={() => setMenuOpen(true)}
          crumbs={crumbs}
          onOpenCommand={openCommand}
        />
        {needsSignIn && !bannerDismissed && (
          <div className="auth-soft-banner" role="status">
            <div className="auth-soft-banner__text">
              <LogIn size={16} aria-hidden />
              <span>{t('auth.softBanner')}</span>
            </div>
            <div className="auth-soft-banner__actions">
              <Button variant="primary" size="sm" onClick={() => void login()}>
                {t('auth.signIn')}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setBannerDismissed(true)}
              >
                {t('auth.dismissBanner')}
              </Button>
            </div>
          </div>
        )}
        <main id="main-content" className="shell__content" tabIndex={-1}>
          <Outlet
            context={{
              openCreate,
              openCommand,
            }}
          />
        </main>
      </div>
      <CreateWorkItemModal
        kind={createKind}
        onClose={() => setCreateKind(null)}
      />
      <CommandPalette
        open={cmdOpen}
        onClose={() => setCmdOpen(false)}
        onCreate={openCreate}
      />
    </div>
  );
}

export interface ShellOutletContext {
  openCreate: (kind: CreateKind) => void;
  openCommand: () => void;
}

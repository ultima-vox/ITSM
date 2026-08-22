import { useCallback, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { LogIn } from 'lucide-react';
import { useAuth } from '@/auth';
import { useT } from '@/i18n';
import { Button } from '@/components/ui';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { AnnouncementBanner } from './AnnouncementBanner';
import {
  CommandPalette,
  useCommandPaletteHotkey,
} from '@/components/command/CommandPalette';
import { adminNav, adminSecondaryNav } from './nav';
import { useExperience } from '@/hooks/useExperience';
import { useCrumbs, useDrawerMenu, type ShellOutletContext } from '@/hooks/useShell';

export function AdminShell() {
  useExperience('admin');
  const t = useT();
  const { needsSignIn, login, loading: authLoading } = useAuth();
  const { menuOpen, setMenuOpen } = useDrawerMenu();
  const [cmdOpen, setCmdOpen] = useState(false);
  const [bannerDismissed, setBannerDismissed] = useState(false);
  const crumbs = useCrumbs('nav.metadata');

  const openCommand = useCallback(() => setCmdOpen(true), []);
  useCommandPaletteHotkey(openCommand);

  const openCreate = useCallback(() => undefined, []);

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
      <Sidebar
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        items={adminNav}
        secondaryItems={adminSecondaryNav}
      />
      <div className="shell__main">
        <Header
          onMenu={() => setMenuOpen(true)}
          crumbs={crumbs}
          onOpenCommand={openCommand}
          homeTo="/admin"
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
        <AnnouncementBanner />
        <main id="main-content" className="shell__content" tabIndex={-1}>
          <Outlet
            context={{
              openCreate,
              openCommand,
            } satisfies ShellOutletContext}
          />
        </main>
      </div>
      <CommandPalette
        open={cmdOpen}
        onClose={() => setCmdOpen(false)}
        onCreate={openCreate}
      />
    </div>
  );
}

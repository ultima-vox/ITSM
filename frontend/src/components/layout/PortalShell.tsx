import { useCallback, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { LogIn } from 'lucide-react';
import { useAuth } from '@/auth';
import { useT } from '@/i18n';
import { Button } from '@/components/ui';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { AnnouncementBanner } from './AnnouncementBanner';
import { portalNav } from './nav';
import { useExperience } from '@/hooks/useExperience';
import { useCrumbs, useDrawerMenu, type ShellOutletContext } from '@/hooks/useShell';

export function PortalShell() {
  useExperience('portal');
  const t = useT();
  const { needsSignIn, login, loading: authLoading } = useAuth();
  const { menuOpen, setMenuOpen } = useDrawerMenu();
  const [bannerDismissed, setBannerDismissed] = useState(false);
  const crumbs = useCrumbs('nav.catalog');

  const openCommand = useCallback(() => undefined, []);
  const openCreate = useCallback(() => undefined, []);

  if (authLoading) {
    return (
      <div className="shell shell--booting" role="status" aria-live="polite">
        <p>{t('app.loading')}</p>
      </div>
    );
  }

  return (
    <div className="shell shell--portal">
      <a href="#main-content" className="skip-link">
        {t('app.skipToContent')}
      </a>
      <Sidebar
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        items={portalNav}
        showWorkspace={false}
      />
      <div className="shell__main">
        <Header
          onMenu={() => setMenuOpen(true)}
          crumbs={crumbs}
          showBrand
          homeTo="/portal"
          navItems={portalNav}
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
    </div>
  );
}

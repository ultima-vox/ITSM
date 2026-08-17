import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Contrast,
  Database,
  Globe,
  HardDrive,
  Languages,
  Moon,
  Plus,
  Server,
  Sun,
} from 'lucide-react';
import { useAuth } from '@/auth';
import { useI18n, useT } from '@/i18n';
import { useDensity } from '@/hooks/useDensity';
import { useTheme, type ThemeMode } from '@/hooks/useTheme';
import { useToast } from '@/hooks/useToast';
import { useAsync } from '@/hooks/useAsync';
import {
  isMockMode,
  getBaseUrl,
  getApiToken,
  fetchPlatformIntegrations,
  SAMPLE_WORK_ITEM_TRANSLATIONS,
  fetchNotificationPreferences,
  updateNotificationPreferences,
} from '@/api';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { resetDemoData } from '@/mock/store';
import { Button, Tabs, Toggle, SkeletonRows, ErrorState } from '@/components/ui';
import type { LocaleCode, NotificationPrefs } from '@/types';
import {
  loadNotificationPrefs,
  saveNotificationPrefs,
  setNotificationPref,
} from '@/lib/notificationPrefs';

function healthTone(status?: string): 'live' | 'mock' | 'warn' {
  const s = (status ?? '').toUpperCase();
  if (s === 'UP' || s === 'OK') return 'live';
  if (s === 'DOWN' || s === 'OUT_OF_SERVICE') return 'warn';
  return 'mock';
}

type SettingsSection =
  | 'profile'
  | 'language'
  | 'appearance'
  | 'notifications'
  | 'api'
  | 'integrations'
  | 'demo';

const THEME_ICONS: Record<ThemeMode, typeof Sun> = {
  light: Sun,
  dark: Moon,
  'high-contrast': Contrast,
};

export function SettingsPage() {
  const t = useT();
  const { locale, setLocale, locales } = useI18n();
  const { density, setDensity } = useDensity();
  const { theme, setTheme } = useTheme();
  const { success, info, error: toastError } = useToast();
  const mockMode = isMockMode();
  const {
    oidcEnabled,
    isAuthenticated,
    user,
    login,
    logout,
    loading: authLoading,
  } = useAuth();
  const currentUser = useCurrentUser();
  const hasToken = Boolean(getApiToken());
  const [section, setSection] = useState<SettingsSection>('profile');
  const [prefs, setPrefs] = useState<NotificationPrefs>(loadNotificationPrefs);

  useEffect(() => {
    if (mockMode) return;
    let cancelled = false;
    void fetchNotificationPreferences().then((remote) => {
      if (cancelled) return;
      setPrefs(remote);
      saveNotificationPrefs(remote);
    }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [mockMode]);

  const {
    data: integrations,
    loading: integrationsLoading,
    error: integrationsError,
    reload: reloadIntegrations,
  } = useAsync(() => fetchPlatformIntegrations(), []);

  const setPref = useCallback(
    <K extends keyof NotificationPrefs>(key: K, value: NotificationPrefs[K]) => {
      setPrefs(setNotificationPref(key, value));
    },
    [],
  );

  const save = async () => {
    saveNotificationPrefs(prefs);
    try {
      const saved = await updateNotificationPreferences(prefs);
      setPrefs(saved);
      saveNotificationPrefs(saved);
      success(t('settings.saved'));
    } catch {
      toastError(t('settings.saveFailed'));
    }
  };

  const onResetDemo = () => {
    if (!window.confirm(t('settings.resetDemoConfirm'))) return;
    resetDemoData();
    success(t('settings.resetDemoDone'));
    info(t('settings.resetDemoHint'));
  };

  const sectionItems = useMemo(
    () => [
      { id: 'profile', label: t('settings.profile') },
      { id: 'language', label: t('settings.language') },
      { id: 'appearance', label: t('settings.appearance') },
      { id: 'notifications', label: t('settings.notifications') },
      { id: 'api', label: t('settings.tabApi') },
      { id: 'integrations', label: t('settings.integrations') },
      { id: 'demo', label: t('settings.tabDemo') },
    ],
    [t],
  );

  const profileName = isAuthenticated && user ? user.name : currentUser.name;
  const profileEmail =
    isAuthenticated && user
      ? user.email || user.username || '—'
      : currentUser.email;
  const profileRole =
    isAuthenticated && user?.roles?.length
      ? user.roles
          .filter(
            (r) =>
              !r.startsWith('default-') &&
              r !== 'offline_access' &&
              r !== 'uma_authorization',
          )
          .join(', ') || currentUser.role
      : currentUser.role;

  let authStatusLabel = t('settings.authStatusAnonymous');
  if (isAuthenticated && user) {
    authStatusLabel = t('settings.authStatusSignedIn', { name: user.name });
  } else if (hasToken) {
    authStatusLabel = t('settings.authStatusToken');
  } else if (!oidcEnabled) {
    authStatusLabel = t('settings.authStatusDisabled');
  }

  return (
    <section className="page page--settings">
      <div className="page-head">
        <div>
          <h1>{t('settings.title')}</h1>
          <p className="page-subtitle">{t('settings.subtitle')}</p>
        </div>
        <Button variant="primary" onClick={() => void save()}>
          {t('app.save')}
        </Button>
      </div>

      <div className="settings-shell">
        <nav className="settings-nav panel" aria-label={t('settings.title')}>
          <Tabs
            className="settings-nav__tabs tabs--vertical"
            items={sectionItems}
            value={section}
            onChange={(id) => setSection(id as SettingsSection)}
          />
        </nav>

        <div className="settings-panels">
          {section === 'profile' && (
            <div className="settings-panel-stack">
              <section className="panel settings-card settings-card--solo" id="settings-profile">
                <h2>{t('settings.profile')}</h2>
                <p className="panel-hint">{t('settings.profileHint')}</p>
                <dl className="settings-dl">
                  <div>
                    <dt>{t('settings.name')}</dt>
                    <dd>{profileName}</dd>
                  </div>
                  <div>
                    <dt>{t('settings.email')}</dt>
                    <dd>{profileEmail}</dd>
                  </div>
                  <div>
                    <dt>{t('settings.role')}</dt>
                    <dd>{profileRole}</dd>
                  </div>
                  {!isAuthenticated && (
                    <>
                      <div>
                        <dt>{t('settings.team')}</dt>
                        <dd>{currentUser.team}</dd>
                      </div>
                      <div>
                        <dt>{t('settings.timezone')}</dt>
                        <dd>{currentUser.timezone}</dd>
                      </div>
                    </>
                  )}
                  {isAuthenticated && user?.sub && (
                    <div>
                      <dt>{t('settings.subject')}</dt>
                      <dd>
                        <code className="settings-mono">{user.sub}</code>
                      </dd>
                    </div>
                  )}
                </dl>
              </section>

              {oidcEnabled && (
                <section className="panel settings-card settings-card--solo">
                  <h2>{t('settings.auth')}</h2>
                  <p className="panel-hint">{t('settings.authHint')}</p>
                  <dl className="settings-dl">
                    <div>
                      <dt>{t('settings.authStatus')}</dt>
                      <dd>
                        <span
                          className={`api-mode-pill${
                            isAuthenticated
                              ? ' api-mode-pill--live'
                              : ' api-mode-pill--mock'
                          }`}
                        >
                          {isAuthenticated
                            ? t('settings.authSignedIn')
                            : t('settings.authSignedOut')}
                        </span>
                      </dd>
                    </div>
                    <div>
                      <dt>{t('settings.authDetail')}</dt>
                      <dd>{authStatusLabel}</dd>
                    </div>
                  </dl>
                  <div className="settings-auth-actions">
                    {!isAuthenticated ? (
                      <Button
                        variant="primary"
                        disabled={authLoading}
                        onClick={() => void login()}
                      >
                        {t('auth.signIn')}
                      </Button>
                    ) : (
                      <Button
                        variant="secondary"
                        disabled={authLoading}
                        onClick={() => void logout()}
                      >
                        {t('auth.signOut')}
                      </Button>
                    )}
                  </div>
                </section>
              )}
            </div>
          )}

          {section === 'language' && (
            <div className="settings-panel-stack">
              <section className="panel settings-card settings-card--solo">
                <h2>{t('settings.language')}</h2>
                <p className="panel-hint">{t('settings.languageHint')}</p>
                <div
                  className="lang-options lang-options--grid"
                  role="radiogroup"
                  aria-label={t('settings.language')}
                >
                  {locales.map((code) => (
                    <label
                      key={code}
                      className={`lang-option${locale === code ? ' is-selected' : ''}`}
                    >
                      <input
                        type="radio"
                        name="locale"
                        value={code}
                        checked={locale === code}
                        onChange={() => setLocale(code as LocaleCode)}
                      />
                      <span>
                        <b>{t(`locale.${code}`)}</b>
                        <small>{code.toUpperCase()}</small>
                      </span>
                    </label>
                  ))}
                </div>

                <div className="settings-lang-admin">
                  <div className="settings-lang-admin__head">
                    <Languages size={15} aria-hidden />
                    <span>{t('settings.supportedLanguages')}</span>
                  </div>
                  <ul className="settings-lang-list">
                    {locales.map((code) => (
                      <li key={code}>
                        <Globe size={14} aria-hidden />
                        <span>{t(`locale.${code}`)}</span>
                        <code className="mono">{code}</code>
                        {locale === code && (
                          <span className="api-mode-pill api-mode-pill--live">
                            {t('settings.localeActive')}
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                  <div className="settings-lang-add">
                    <Button
                      variant="secondary"
                      size="sm"
                      disabled
                      icon={<Plus size={14} />}
                    >
                      {t('settings.addLanguage')}
                    </Button>
                    <p className="settings-soon-note">{t('settings.addLanguageSoon')}</p>
                  </div>
                </div>
              </section>

              <section className="panel settings-card settings-card--solo">
                <h2>{t('settings.translationAdmin')}</h2>
                <p className="panel-hint">{t('settings.translationAdminHint')}</p>
                <div className="data-table-wrap translation-admin-table">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th scope="col">{t('settings.colNamespace')}</th>
                        <th scope="col">{t('settings.colKey')}</th>
                        <th scope="col">EN</th>
                        <th scope="col">RU</th>
                        <th scope="col">DE</th>
                      </tr>
                    </thead>
                    <tbody>
                      {SAMPLE_WORK_ITEM_TRANSLATIONS.map((row) => (
                        <tr key={`${row.namespace}.${row.key}`}>
                          <td>
                            <code className="mono">{row.namespace}</code>
                          </td>
                          <td>
                            <code className="mono">{row.key}</code>
                          </td>
                          <td>{row.en}</td>
                          <td>{row.ru}</td>
                          <td>{row.de}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="settings-soon-note">
                  {t('settings.translationAdminReadOnly')}
                </p>
              </section>
            </div>
          )}

          {section === 'appearance' && (
            <section className="panel settings-card settings-card--solo">
              <h2>{t('settings.appearance')}</h2>
              <p className="panel-hint">{t('settings.appearanceHint')}</p>

              <div className="appearance-block">
                <p className="field__label" id="settings-theme-label">
                  {t('settings.theme')}
                </p>
                <div
                  className="appearance-theme-grid"
                  role="radiogroup"
                  aria-labelledby="settings-theme-label"
                >
                  {(['light', 'dark', 'high-contrast'] as ThemeMode[]).map((mode) => {
                    const Icon = THEME_ICONS[mode];
                    const selected = theme === mode;
                    const nameKey =
                      mode === 'high-contrast'
                        ? 'settings.theme_highContrast'
                        : `settings.theme_${mode}`;
                    const hintKey =
                      mode === 'high-contrast'
                        ? 'settings.theme_highContrastHint'
                        : `settings.theme_${mode}Hint`;
                    return (
                      <label
                        key={mode}
                        className={`appearance-theme-card${selected ? ' is-selected' : ''}`}
                      >
                        <input
                          type="radio"
                          name="theme"
                          value={mode}
                          checked={selected}
                          onChange={() => setTheme(mode)}
                        />
                        <span className="appearance-theme-card__icon" aria-hidden>
                          <Icon size={18} />
                        </span>
                        <span className="appearance-theme-card__text">
                          <b>{t(nameKey)}</b>
                          <small>{t(hintKey)}</small>
                        </span>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="appearance-block appearance-block--density">
                <p className="field__label" id="settings-density-label">
                  {t('app.density')}
                </p>
                <div
                  className="appearance-density"
                  role="radiogroup"
                  aria-labelledby="settings-density-label"
                >
                  <label
                    className={`appearance-density__opt${
                      density === 'comfortable' ? ' is-selected' : ''
                    }`}
                  >
                    <input
                      type="radio"
                      name="density"
                      value="comfortable"
                      checked={density === 'comfortable'}
                      onChange={() => setDensity('comfortable')}
                    />
                    <span>
                      <b>{t('app.densityComfortable')}</b>
                      <small>{t('settings.densityComfortableHint')}</small>
                    </span>
                  </label>
                  <label
                    className={`appearance-density__opt${
                      density === 'compact' ? ' is-selected' : ''
                    }`}
                  >
                    <input
                      type="radio"
                      name="density"
                      value="compact"
                      checked={density === 'compact'}
                      onChange={() => setDensity('compact')}
                    />
                    <span>
                      <b>{t('app.densityCompact')}</b>
                      <small>{t('settings.densityCompactHint')}</small>
                    </span>
                  </label>
                </div>
              </div>
            </section>
          )}

          {section === 'notifications' && (
            <section className="panel settings-card settings-card--solo">
              <h2>{t('settings.notifications')}</h2>
              <p className="panel-hint">{t('settings.notificationsHint')}</p>
              <p className="settings-persist-note">{t(mockMode ? 'settings.notificationsPersist' : 'settings.notificationsPersistServer')}</p>
              <div className="toggle-stack">
                <Toggle
                  label={t('settings.prefEmail')}
                  checked={prefs.email}
                  onChange={(v) => setPref('email', v)}
                />
                <Toggle
                  label={t('settings.prefDesktop')}
                  checked={prefs.desktop}
                  onChange={(v) => setPref('desktop', v)}
                />
                <Toggle
                  label={t('settings.prefSla')}
                  checked={prefs.slaAlerts}
                  onChange={(v) => setPref('slaAlerts', v)}
                />
                <Toggle
                  label={t('settings.prefAssignment')}
                  checked={prefs.assignment}
                  onChange={(v) => setPref('assignment', v)}
                />
                <Toggle
                  label={t('settings.prefMentions')}
                  checked={prefs.mentions}
                  onChange={(v) => setPref('mentions', v)}
                />
              </div>
            </section>
          )}

          {section === 'api' && (
            <section className="panel settings-card settings-card--solo">
              <h2>{t('settings.apiMode')}</h2>
              <p className="panel-hint">{t('settings.apiModeHint')}</p>
              <dl className="settings-dl">
                <div>
                  <dt>{t('settings.apiModeLabel')}</dt>
                  <dd>
                    <span
                      className={`api-mode-pill${
                        mockMode ? ' api-mode-pill--mock' : ' api-mode-pill--live'
                      }`}
                    >
                      {mockMode
                        ? t('settings.apiModeMock')
                        : t('settings.apiModeLive')}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>{t('settings.apiBase')}</dt>
                  <dd>
                    <code>{getBaseUrl()}</code>
                  </dd>
                </div>
              </dl>
              <p className="settings-meta-link">
                <Link to="/admin/metadata">{t('settings.openMetadata')}</Link>
              </p>
            </section>
          )}

          {section === 'integrations' && (
            <section className="panel settings-card settings-card--solo">
              <h2>{t('settings.integrations')}</h2>
              <p className="panel-hint">{t('settings.integrationsHint')}</p>
              {integrationsError && !integrations ? (
                <ErrorState onRetry={reloadIntegrations} />
              ) : integrationsLoading && !integrations ? (
                <SkeletonRows rows={3} />
              ) : integrations ? (
                <div className="integration-cards">
                  <article className="integration-card">
                    <div className="integration-card__icon" aria-hidden>
                      <Server size={18} />
                    </div>
                    <div className="integration-card__body">
                      <header>
                        <h3>Redis</h3>
                        <span
                          className={`api-mode-pill api-mode-pill--${
                            integrations.redis.enabled
                              ? healthTone(integrations.redis.health?.status)
                              : 'mock'
                          }`}
                        >
                          {integrations.redis.enabled
                            ? integrations.redis.health?.status ??
                              t('settings.integrationOn')
                            : t('settings.integrationOff')}
                        </span>
                      </header>
                      <dl className="settings-dl settings-dl--compact">
                        <div>
                          <dt>{t('settings.integrationHost')}</dt>
                          <dd>
                            <code>
                              {integrations.redis.host}:{integrations.redis.port}
                            </code>
                          </dd>
                        </div>
                        <div>
                          <dt>{t('settings.integrationEnabled')}</dt>
                          <dd>
                            {integrations.redis.enabled ? t('app.yes') : t('app.no')}
                          </dd>
                        </div>
                      </dl>
                    </div>
                  </article>

                  <article className="integration-card">
                    <div className="integration-card__icon" aria-hidden>
                      <Database size={18} />
                    </div>
                    <div className="integration-card__body">
                      <header>
                        <h3>OpenSearch</h3>
                        <span
                          className={`api-mode-pill api-mode-pill--${
                            integrations.opensearch.enabled
                              ? healthTone(integrations.opensearch.health?.status)
                              : 'mock'
                          }`}
                        >
                          {integrations.opensearch.enabled
                            ? integrations.opensearch.health?.status ??
                              t('settings.integrationOn')
                            : t('settings.integrationOff')}
                        </span>
                      </header>
                      <dl className="settings-dl settings-dl--compact">
                        <div>
                          <dt>{t('settings.integrationUrl')}</dt>
                          <dd>
                            <code className="settings-mono">
                              {integrations.opensearch.url || '—'}
                            </code>
                          </dd>
                        </div>
                        <div>
                          <dt>{t('settings.integrationIndex')}</dt>
                          <dd>
                            <code>{integrations.opensearch.index || '—'}</code>
                          </dd>
                        </div>
                      </dl>
                    </div>
                  </article>

                  <article className="integration-card">
                    <div className="integration-card__icon" aria-hidden>
                      <HardDrive size={18} />
                    </div>
                    <div className="integration-card__body">
                      <header>
                        <h3>S3 / MinIO</h3>
                        <span
                          className={`api-mode-pill api-mode-pill--${
                            integrations.storage.type ? 'live' : 'mock'
                          }`}
                        >
                          {integrations.storage.type || t('settings.integrationOff')}
                        </span>
                      </header>
                      <dl className="settings-dl settings-dl--compact">
                        <div>
                          <dt>{t('settings.integrationEndpoint')}</dt>
                          <dd>
                            <code className="settings-mono">
                              {integrations.storage.endpoint || '—'}
                            </code>
                          </dd>
                        </div>
                        <div>
                          <dt>{t('settings.integrationBucket')}</dt>
                          <dd>
                            <code>{integrations.storage.bucket || '—'}</code>
                          </dd>
                        </div>
                      </dl>
                    </div>
                  </article>
                </div>
              ) : null}
            </section>
          )}

          {section === 'demo' && (
            <section className="panel settings-card settings-card--solo">
              <h2>{t('settings.tabDemo')}</h2>
              <p className="panel-hint">{t('settings.resetDemoHint')}</p>
              {mockMode ? (
                <div className="settings-demo-reset settings-demo-reset--solo">
                  <Button variant="secondary" onClick={onResetDemo}>
                    {t('settings.resetDemo')}
                  </Button>
                  <p className="settings-soon-note">{t('settings.demoOnlyMock')}</p>
                </div>
              ) : (
                <p className="settings-soon-note">{t('settings.demoOnlyMock')}</p>
              )}
            </section>
          )}
        </div>
      </div>
    </section>
  );
}

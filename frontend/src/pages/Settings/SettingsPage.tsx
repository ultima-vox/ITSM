import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Database,
  Globe,
  HardDrive,
  Languages,
  Plus,
  Server,
} from 'lucide-react';
import { useAuth } from '@/auth';
import { useI18n, useT } from '@/i18n';
import { useDensity } from '@/hooks/useDensity';
import { useTheme, type ThemeMode } from '@/hooks/useTheme';
import { useToast } from '@/hooks/useToast';
import { useAsync } from '@/hooks/useAsync';
import {
  useMock,
  getBaseUrl,
  getApiToken,
  fetchPlatformIntegrations,
  SAMPLE_WORK_ITEM_TRANSLATIONS,
} from '@/api';
import { currentUser } from '@/mock/data';
import { Button, Toggle, SkeletonRows, ErrorState } from '@/components/ui';
import type { LocaleCode, NotificationPrefs } from '@/types';

function healthTone(status?: string): 'live' | 'mock' | 'warn' {
  const s = (status ?? '').toUpperCase();
  if (s === 'UP' || s === 'OK') return 'live';
  if (s === 'DOWN' || s === 'OUT_OF_SERVICE') return 'warn';
  return 'mock';
}

export function SettingsPage() {
  const t = useT();
  const { locale, setLocale, locales } = useI18n();
  const { density, setDensity } = useDensity();
  const { theme, setTheme } = useTheme();
  const { success } = useToast();
  const mockMode = useMock();
  const {
    oidcEnabled,
    isAuthenticated,
    user,
    login,
    logout,
    loading: authLoading,
  } = useAuth();
  const hasToken = Boolean(getApiToken());
  const [prefs, setPrefs] = useState<NotificationPrefs>({
    email: true,
    desktop: false,
    slaAlerts: true,
    assignment: true,
    mentions: true,
  });

  const {
    data: integrations,
    loading: integrationsLoading,
    error: integrationsError,
    reload: reloadIntegrations,
  } = useAsync(() => fetchPlatformIntegrations(), []);

  const save = () => {
    success(t('settings.saved'));
  };

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
        <Button variant="primary" onClick={save}>
          {t('app.save')}
        </Button>
      </div>

      <div className="settings-grid">
        <section className="panel settings-card">
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
          <section className="panel settings-card">
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

        <section className="panel settings-card">
          <h2>{t('settings.language')}</h2>
          <p className="panel-hint">{t('settings.languageHint')}</p>
          <div className="lang-options" role="radiogroup" aria-label={t('settings.language')}>
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
              <Button variant="secondary" size="sm" disabled icon={<Plus size={14} />}>
                {t('settings.addLanguage')}
              </Button>
              <p className="settings-soon-note">{t('settings.addLanguageSoon')}</p>
            </div>
          </div>
        </section>

        <section className="panel settings-card">
          <h2>{t('settings.apiMode')}</h2>
          <p className="panel-hint">{t('settings.apiModeHint')}</p>
          <dl className="settings-dl">
            <div>
              <dt>{t('settings.apiModeLabel')}</dt>
              <dd>
                <span
                  className={`api-mode-pill${mockMode ? ' api-mode-pill--mock' : ' api-mode-pill--live'}`}
                >
                  {mockMode ? t('settings.apiModeMock') : t('settings.apiModeLive')}
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

        <section className="panel settings-card settings-card--wide">
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
                        ? integrations.redis.health?.status ?? t('settings.integrationOn')
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

        <section className="panel settings-card">
          <h2>{t('settings.notifications')}</h2>
          <p className="panel-hint">{t('settings.notificationsHint')}</p>
          <div className="toggle-stack">
            <Toggle
              label={t('settings.prefEmail')}
              checked={prefs.email}
              onChange={(v) => setPrefs((p) => ({ ...p, email: v }))}
            />
            <Toggle
              label={t('settings.prefDesktop')}
              checked={prefs.desktop}
              onChange={(v) => setPrefs((p) => ({ ...p, desktop: v }))}
            />
            <Toggle
              label={t('settings.prefSla')}
              checked={prefs.slaAlerts}
              onChange={(v) => setPrefs((p) => ({ ...p, slaAlerts: v }))}
            />
            <Toggle
              label={t('settings.prefAssignment')}
              checked={prefs.assignment}
              onChange={(v) => setPrefs((p) => ({ ...p, assignment: v }))}
            />
            <Toggle
              label={t('settings.prefMentions')}
              checked={prefs.mentions}
              onChange={(v) => setPrefs((p) => ({ ...p, mentions: v }))}
            />
          </div>
        </section>

        <section className="panel settings-card">
          <h2>{t('settings.appearance')}</h2>
          <p className="panel-hint">{t('settings.appearanceHint')}</p>
          <p className="field__label" style={{ marginBottom: 8 }}>
            {t('settings.theme')}
          </p>
          <div className="lang-options" role="radiogroup" aria-label={t('settings.theme')}>
            {(['light', 'dark', 'high-contrast'] as ThemeMode[]).map((mode) => (
              <label
                key={mode}
                className={`lang-option${theme === mode ? ' is-selected' : ''}`}
              >
                <input
                  type="radio"
                  name="theme"
                  value={mode}
                  checked={theme === mode}
                  onChange={() => setTheme(mode)}
                />
                <span>
                  <b>
                    {t(
                      `settings.theme_${mode === 'high-contrast' ? 'highContrast' : mode}`,
                    )}
                  </b>
                  <small>
                    {t(
                      `settings.theme_${mode === 'high-contrast' ? 'highContrast' : mode}Hint`,
                    )}
                  </small>
                </span>
              </label>
            ))}
          </div>
          <div style={{ marginTop: 16 }}>
            <Toggle
              label={t('app.density')}
              description={
                density === 'compact'
                  ? t('app.densityCompact')
                  : t('app.densityComfortable')
              }
              checked={density === 'compact'}
              onChange={(v) => setDensity(v ? 'compact' : 'comfortable')}
            />
          </div>
        </section>

        <section className="panel settings-card settings-card--wide">
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
          <p className="settings-soon-note">{t('settings.translationAdminReadOnly')}</p>
        </section>
      </div>
    </section>
  );
}

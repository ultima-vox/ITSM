import { Fingerprint, KeyRound, Users } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { fetchIdentityAccounts, fetchIdentityGroupMappings, isMockMode } from '@/api';
import { Badge, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import { useAsync } from '@/hooks/useAsync';

export function IdentityPage() {
  const t = useT();
  const { locale } = useI18n();
  const liveMode = !isMockMode();
  const accountsState = useAsync(() => fetchIdentityAccounts(), []);
  const mappingsState = useAsync(() => fetchIdentityGroupMappings(), []);

  const accounts = accountsState.data ?? [];
  const mappings = mappingsState.data ?? [];
  const loadError = accountsState.error || mappingsState.error;
  const loading =
    (accountsState.loading && !accountsState.data) ||
    (mappingsState.loading && !mappingsState.data);

  const retry = () => {
    accountsState.reload();
    mappingsState.reload();
  };

  if (loadError && !loading && accounts.length === 0 && mappings.length === 0) {
    return (
      <section className="page page--identity">
        <div className="page-head">
          <div>
            <h1>{t('identityAdmin.title')}</h1>
            <p className="page-subtitle">{t('identityAdmin.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={retry} />
      </section>
    );
  }

  return (
    <section className="page page--identity">
      <div className="page-head">
        <div>
          <h1>{t('identityAdmin.title')}</h1>
          <p className="page-subtitle">{t('identityAdmin.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            <Users size={14} aria-hidden />
            {t('identityAdmin.accountCount', { n: accounts.length })}
          </span>
          <span className="chip chip--muted">
            <KeyRound size={14} aria-hidden />
            {t('identityAdmin.mappingCount', { n: mappings.length })}
          </span>
          <span className={`chip${liveMode ? '' : ' chip--muted'}`}>
            {liveMode ? t('identityAdmin.liveHint') : t('identityAdmin.mockHint')}
          </span>
        </div>
      </div>

      {loading && (
        <div className="panel" aria-busy="true">
          <Skeleton height={36} />
          <Skeleton height={36} className="mt-2" />
          <Skeleton height={36} className="mt-2" />
        </div>
      )}

      {!loading && (
        <>
          <div className="panel panel--flush">
            <div className="rbac-admin-section-label">
              <Fingerprint size={15} aria-hidden />
              <h2>{t('identityAdmin.accounts')}</h2>
              <span className="rbac-admin-section-label__count">{accounts.length}</span>
            </div>
            {accounts.length === 0 ? (
              <EmptyState
                title={t('identityAdmin.emptyAccountsTitle')}
                description={t('identityAdmin.emptyAccountsHint')}
                icon={<Fingerprint size={22} />}
              />
            ) : (
              <div className="data-table-wrap">
                <table className="data-table data-table--dense">
                  <thead>
                    <tr>
                      <th scope="col">{t('identityAdmin.colIdp')}</th>
                      <th scope="col">{t('identityAdmin.colExternalId')}</th>
                      <th scope="col">{t('identityAdmin.colSubject')}</th>
                      <th scope="col">{t('identityAdmin.colEnabled')}</th>
                      <th scope="col">{t('identityAdmin.colLastSync')}</th>
                      <th scope="col">{t('identityAdmin.colRoles')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {accounts.map((row) => (
                      <tr key={row.id}>
                        <td className="mono">{row.idp}</td>
                        <td className="mono">{row.externalId}</td>
                        <td className="mono">{row.subjectId}</td>
                        <td>
                          <Badge tone={row.enabled ? 'mint' : 'rose'} dot>
                            {row.enabled
                              ? t('identityAdmin.enabled')
                              : t('identityAdmin.disabled')}
                          </Badge>
                        </td>
                        <td>
                          {row.lastSync ? (
                            <time dateTime={row.lastSync}>
                              {formatDateTime(row.lastSync, locale)}
                            </time>
                          ) : (
                            t('identityAdmin.neverSynced')
                          )}
                        </td>
                        <td>
                          {row.roleKeys.length === 0
                            ? t('app.none')
                            : row.roleKeys.map((key) => (
                                <code key={key} className="mono rbac-perm-chip">
                                  {key}
                                </code>
                              ))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="panel panel--flush mt-4">
            <div className="rbac-admin-section-label">
              <KeyRound size={15} aria-hidden />
              <h2>{t('identityAdmin.mappings')}</h2>
              <span className="rbac-admin-section-label__count">{mappings.length}</span>
            </div>
            {mappings.length === 0 ? (
              <EmptyState
                title={t('identityAdmin.emptyMappingsTitle')}
                description={t('identityAdmin.emptyMappingsHint')}
                icon={<KeyRound size={22} />}
              />
            ) : (
              <div className="data-table-wrap">
                <table className="data-table data-table--dense">
                  <thead>
                    <tr>
                      <th scope="col">{t('identityAdmin.colIdpGroup')}</th>
                      <th scope="col">{t('identityAdmin.colRole')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappings.map((row) => (
                      <tr key={row.idpGroup}>
                        <td className="mono">{row.idpGroup}</td>
                        <td>
                          <code className="mono">{row.roleName}</code>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </section>
  );
}

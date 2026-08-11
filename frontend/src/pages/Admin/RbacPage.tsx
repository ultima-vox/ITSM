import { useCallback, useEffect, useMemo, useState } from 'react';
import { KeyRound, Shield, Users, UserRoundCheck } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import {
  assignUserRole,
  fetchRbacRoles,
  fetchRbacUsers,
  getPermissionDescription,
  rbacWritable,
  subscribeRbac,
  isMockMode,
  fetchRoleDelegations,
  createRoleDelegation,
  revokeRoleDelegation,
} from '@/api';
import type { RoleDelegation } from '@/api';
import type { LocaleCode, RbacRole, RbacRoleKey, RbacUser, RbacUserStatus } from '@/types';
import { Badge, Button, EmptyState, ErrorState, Input, Select, Tabs } from '@/components/ui';
import { useToast } from '@/hooks/useToast';

const ROLE_CHIP_LIMIT = 6;

function roleLabel(role: RbacRole, locale: LocaleCode): string {
  if (locale === 'ru' && role.labels.ru) return role.labels.ru;
  if (locale === 'de' && role.labels.de) return role.labels.de;
  return role.labels.en;
}

function statusTone(
  status: RbacUserStatus,
): 'mint' | 'neutral' | 'rose' | 'amber' {
  switch (status) {
    case 'active':
      return 'mint';
    case 'locked':
      return 'rose';
    case 'inactive':
      return 'neutral';
    default:
      return 'amber';
  }
}

export function RbacPage() {
  const t = useT();
  const { locale } = useI18n();
  const { success, error } = useToast();
  const writable = rbacWritable();
  const liveMode = !isMockMode();
  const [tab, setTab] = useState<'roles' | 'users' | 'delegations'>('roles');
  const [roles, setRoles] = useState<RbacRole[]>([]);
  const [users, setUsers] = useState<RbacUser[]>([]);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [delegations, setDelegations] = useState<RoleDelegation[]>([]);
  const [delegatorId, setDelegatorId] = useState('');
  const [delegateeId, setDelegateeId] = useState('');
  const [delegationRole, setDelegationRole] = useState('SERVICE_DESK_AGENT');
  const [delegationReason, setDelegationReason] = useState('');
  const [delegationSaving, setDelegationSaving] = useState(false);

  const reload = useCallback(async () => {
    try {
      const [r, u, d] = await Promise.all([
        fetchRbacRoles(), fetchRbacUsers(), fetchRoleDelegations(),
      ]);
      setRoles(r);
      setUsers(u);
      setDelegations(d);
      setLoadError(false);
    } catch {
      setLoadError(true);
    }
  }, []);

  useEffect(() => {
    void reload();
    return subscribeRbac(() => {
      void reload();
    });
  }, [reload]);

  const selected: RbacRole | null = useMemo(() => {
    if (!roles.length) return null;
    const key = selectedKey ?? roles[0]?.roleKey ?? null;
    return roles.find((r) => r.roleKey === key) ?? roles[0] ?? null;
  }, [roles, selectedKey]);

  const roleOptions = useMemo(
    () =>
      roles.map((r) => ({
        value: r.roleKey,
        label: `${r.roleKey} — ${roleLabel(r, locale)}`,
      })),
    [roles, locale],
  );

  const handleAssign = (user: RbacUser, roleKey: RbacRoleKey) => {
    if (!writable) return;
    if (user.roleKey === roleKey) return;
    void assignUserRole(user.id, roleKey).then((next) => {
      if (next) {
        success(
          t('rbacAdmin.roleAssignedToast', { name: user.name, role: roleKey }),
        );
        void reload();
      }
    });
  };

  const handleDelegate = async () => {
    setDelegationSaving(true);
    try {
      const expiresAt = new Date(Date.now() + 7 * 86_400_000).toISOString();
      await createRoleDelegation({
        delegatorId, delegateeId, roleKey: delegationRole, expiresAt,
        reason: delegationReason,
      });
      success(t('rbacAdmin.delegationCreated'));
      setDelegateeId('');
      setDelegationReason('');
      await reload();
    } catch {
      error(t('rbacAdmin.delegationFailed'));
    } finally {
      setDelegationSaving(false);
    }
  };

  if (loadError) {
    return (
      <section className="page page--rbac">
        <div className="page-head">
          <div>
            <h1>{t('rbacAdmin.title')}</h1>
            <p className="page-subtitle">{t('rbacAdmin.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={() => void reload()} />
      </section>
    );
  }

  return (
    <section className="page page--rbac">
      <div className="page-head">
        <div>
          <h1>{t('rbacAdmin.title')}</h1>
          <p className="page-subtitle">{t('rbacAdmin.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            <Shield size={14} aria-hidden />
            {t('rbacAdmin.roleCount', { n: roles.length })}
          </span>
          <span className="chip chip--muted">
            <Users size={14} aria-hidden />
            {t('rbacAdmin.userCount', { n: users.length })}
          </span>
          <span className={`chip${liveMode ? '' : ' chip--muted'}`}>
            {liveMode ? t('settings.apiModeLive') : t('rbacAdmin.mockHint')}
          </span>
        </div>
      </div>

      <Tabs
        className="rbac-admin-tabs"
        value={tab}
        onChange={(id) => setTab(id as 'roles' | 'users' | 'delegations')}
        items={[
          { id: 'roles', label: t('rbacAdmin.tabRoles'), count: roles.length },
          { id: 'users', label: t('rbacAdmin.tabUsers'), count: users.length },
          { id: 'delegations', label: t('rbacAdmin.tabDelegations'), count: delegations.filter((d) => !d.revokedAt).length },
        ]}
      />

      {tab === 'roles' && (
        <div className="rbac-admin-layout">
          <aside className="panel rbac-admin-list" aria-label={t('rbacAdmin.roles')}>
            <div className="rbac-admin-list__head">
              <KeyRound size={16} aria-hidden />
              <h2>{t('rbacAdmin.roles')}</h2>
            </div>
            {roles.length === 0 ? (
              <EmptyState
                title={t('rbacAdmin.emptyRolesTitle')}
                description={t('rbacAdmin.emptyRolesHint')}
              />
            ) : (
              <ul className="rbac-admin-list__items">
                {roles.map((role) => {
                  const isSel = selected?.roleKey === role.roleKey;
                  const shown = role.permissions.slice(0, ROLE_CHIP_LIMIT);
                  const extra = role.permissions.length - shown.length;
                  return (
                    <li key={role.id}>
                      <button
                        type="button"
                        className={`rbac-admin-btn${isSel ? ' is-active' : ''}`}
                        onClick={() => setSelectedKey(role.roleKey)}
                        aria-current={isSel ? 'true' : undefined}
                      >
                        <span className="rbac-admin-btn__row">
                          <span className="rbac-admin-btn__key mono">{role.roleKey}</span>
                          <Badge tone={role.roleKey === 'ADMIN' ? 'violet' : 'neutral'}>
                            {t('rbacAdmin.permCount', { n: role.permissions.length })}
                          </Badge>
                        </span>
                        <span className="rbac-admin-btn__name">
                          {roleLabel(role, locale)}
                        </span>
                        <span className="rbac-admin-btn__chips" aria-hidden>
                          {shown.map((p) => (
                            <span key={p} className="rbac-perm-chip">
                              {p}
                            </span>
                          ))}
                          {extra > 0 && (
                            <span className="rbac-perm-chip rbac-perm-chip--more">
                              +{extra}
                            </span>
                          )}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </aside>

          <div className="panel panel--flush rbac-admin-detail">
            {!selected ? (
              <div className="rbac-admin-detail__empty">
                <EmptyState
                  title={t('rbacAdmin.selectRoleTitle')}
                  description={t('rbacAdmin.selectRoleHint')}
                />
              </div>
            ) : (
              <>
                <div className="rbac-admin-detail__head">
                  <div>
                    <p className="rbac-admin-detail__kicker">{t('rbacAdmin.role')}</p>
                    <h2>
                      <code className="mono">{selected.roleKey}</code>
                      <span className="rbac-admin-detail__label">
                        {roleLabel(selected, locale)}
                      </span>
                    </h2>
                    <p className="rbac-admin-detail__desc">{selected.description}</p>
                  </div>
                  <div className="rbac-admin-detail__badges">
                    <Badge tone={selected.roleKey === 'ADMIN' ? 'violet' : 'blue'}>
                      {t('rbacAdmin.permCount', { n: selected.permissions.length })}
                    </Badge>
                    <Badge tone="neutral">{t('rbacAdmin.readOnly')}</Badge>
                  </div>
                </div>

                <div className="rbac-admin-section-label">
                  <Shield size={15} aria-hidden />
                  <h3>{t('rbacAdmin.permissions')}</h3>
                  <span className="rbac-admin-section-label__count">
                    {selected.permissions.length}
                  </span>
                </div>

                {selected.permissions.length === 0 ? (
                  <div className="rbac-admin-detail__empty--inline">
                    <EmptyState
                      title={t('rbacAdmin.permsEmptyTitle')}
                      description={t('rbacAdmin.permsEmptyHint')}
                    />
                  </div>
                ) : (
                  <div className="data-table-wrap rbac-admin-perm-table">
                    <table className="data-table data-table--dense">
                      <thead>
                        <tr>
                          <th scope="col">{t('rbacAdmin.colPermission')}</th>
                          <th scope="col">{t('rbacAdmin.colDescription')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selected.permissions.map((key) => (
                          <tr key={key}>
                            <td>
                              <code className="mono rbac-admin-perm-key">{key}</code>
                            </td>
                            <td className="rbac-admin-perm-desc">
                              {getPermissionDescription(key)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {tab === 'users' && (
        <div className="panel panel--flush rbac-admin-users">
          {users.length === 0 ? (
            <EmptyState
              title={t('rbacAdmin.emptyUsersTitle')}
              description={t('rbacAdmin.emptyUsersHint')}
              icon={<Users size={22} />}
            />
          ) : (
            <div className="data-table-wrap rbac-admin-users-table">
              <table className="data-table data-table--dense">
                <thead>
                  <tr>
                    <th scope="col">{t('rbacAdmin.colName')}</th>
                    <th scope="col">{t('rbacAdmin.colRole')}</th>
                    <th scope="col">{t('rbacAdmin.colLocale')}</th>
                    <th scope="col">{t('rbacAdmin.colStatus')}</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id}>
                      <td>
                        <div className="rbac-admin-user">
                          <span className="rbac-admin-user__avatar" aria-hidden>
                            {u.initials}
                          </span>
                          <div className="rbac-admin-user__meta">
                            <strong>{u.name}</strong>
                            <span className="rbac-admin-user__email">{u.email}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <Select
                          id={`rbac-role-${u.id}`}
                          className="rbac-admin-role-select"
                          aria-label={t('rbacAdmin.assignRoleAria', { name: u.name })}
                          options={roleOptions}
                          value={u.roleKey}
                          disabled={!writable}
                          onChange={(e) =>
                            handleAssign(u, e.target.value as RbacRoleKey)
                          }
                        />
                      </td>
                      <td>
                        <Badge tone="neutral">{u.locale.toUpperCase()}</Badge>
                      </td>
                      <td>
                        <Badge tone={statusTone(u.status)} dot>
                          {t(`rbacAdmin.status.${u.status}`)}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === 'delegations' && (
        <div className="panel rbac-admin-users">
          {liveMode && writable && (
            <div className="form-grid mb-4">
              <Input aria-label={t('rbacAdmin.delegator')} placeholder={t('rbacAdmin.delegator')}
                value={delegatorId} onChange={(e) => setDelegatorId(e.target.value)} />
              <Input aria-label={t('rbacAdmin.delegatee')} placeholder={t('rbacAdmin.delegatee')}
                value={delegateeId} onChange={(e) => setDelegateeId(e.target.value)} />
              <Select id="delegation-role" aria-label={t('rbacAdmin.colRole')}
                options={roleOptions.filter((r) => r.value !== 'ADMIN')}
                value={delegationRole} onChange={(e) => setDelegationRole(e.target.value)} />
              <Input aria-label={t('rbacAdmin.reason')} placeholder={t('rbacAdmin.reason')}
                value={delegationReason} onChange={(e) => setDelegationReason(e.target.value)} />
              <Button disabled={delegationSaving || !delegatorId.trim() || !delegateeId.trim() || !delegationReason.trim()}
                onClick={() => void handleDelegate()}>{t('rbacAdmin.createDelegation')}</Button>
            </div>
          )}
          {delegations.length === 0 ? (
            <EmptyState icon={<UserRoundCheck size={22} />} title={t('rbacAdmin.emptyDelegations')}
              description={t('rbacAdmin.emptyDelegationsHint')} />
          ) : (
            <div className="data-table-wrap"><table className="data-table data-table--dense">
              <thead><tr><th>{t('rbacAdmin.delegator')}</th><th>{t('rbacAdmin.delegatee')}</th>
                <th>{t('rbacAdmin.colRole')}</th><th>{t('rbacAdmin.expires')}</th><th>{t('rbacAdmin.colStatus')}</th><th /></tr></thead>
              <tbody>{delegations.map((d) => <tr key={d.id}>
                <td>{d.delegatorId}</td><td>{d.delegateeId}</td><td><code>{d.roleKey}</code></td>
                <td>{new Date(d.expiresAt).toLocaleString(locale)}</td>
                <td><Badge tone={d.revokedAt ? 'neutral' : 'mint'}>{d.revokedAt ? t('rbacAdmin.revoked') : t('rbacAdmin.active')}</Badge></td>
                <td>{!d.revokedAt && <Button variant="secondary" onClick={() => void revokeRoleDelegation(d.id).then(reload).catch(() => error(t('rbacAdmin.delegationFailed')))}>{t('rbacAdmin.revoke')}</Button>}</td>
              </tr>)}</tbody>
            </table></div>
          )}
        </div>
      )}
    </section>
  );
}

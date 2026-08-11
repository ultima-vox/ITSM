import { useCallback, useEffect, useMemo, useState } from 'react';
import { GitBranch, Workflow } from 'lucide-react';
import { useT } from '@/i18n';
import {
  fetchWorkflowDefinitions,
  setWorkflowActiveVersion,
  subscribeWorkflowDefinitions,
  isMockMode,
  workflowDefinitionsWritable,
  fetchWorkflowInstance,
  migrateWorkflowInstance,
  fetchWorkflowApprovals,
  requestWorkflowApproval,
  voteWorkflowApproval,
  fetchWorkflowTimers,
} from '@/api';
import type { WorkflowApprovalView, WorkflowInstanceView, WorkflowTimerView } from '@/api';
import type { WorkflowDefinition } from '@/types';
import { Badge, Button, EmptyState, ErrorState, Input, Select, Toggle } from '@/components/ui';
import { useToast } from '@/hooks/useToast';

export function WorkflowPage() {
  const t = useT();
  const writable = workflowDefinitionsWritable();
  const liveMode = !isMockMode();
  const { success, error } = useToast();
  const [defs, setDefs] = useState<WorkflowDefinition[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [migrationObjectType, setMigrationObjectType] = useState('work-item');
  const [migrationObjectId, setMigrationObjectId] = useState('');
  const [migrationInstance, setMigrationInstance] = useState<WorkflowInstanceView | null>(null);
  const [migrationTarget, setMigrationTarget] = useState('');
  const [approvalTransition, setApprovalTransition] = useState('');
  const [approvalItems, setApprovalItems] = useState<WorkflowApprovalView[]>([]);
  const [timerItems, setTimerItems] = useState<WorkflowTimerView[]>([]);

  const reload = useCallback(async () => {
    try {
      const list = await fetchWorkflowDefinitions();
      setDefs(list);
      setLoadError(false);
    } catch {
      setLoadError(true);
    }
  }, []);

  useEffect(() => {
    void reload();
    return subscribeWorkflowDefinitions(() => {
      void reload();
    });
  }, [reload]);

  const selected: WorkflowDefinition | null = useMemo(() => {
    if (!defs.length) return null;
    const id = selectedId ?? defs[0]?.id ?? null;
    return defs.find((d) => d.id === id) ?? defs[0] ?? null;
  }, [defs, selectedId]);

  const activeCount = defs.filter((d) => d.active).length;
  const objectKeys = useMemo(
    () => new Set(defs.map((d) => d.objectKey)).size,
    [defs],
  );

  const handleActiveToggle = (def: WorkflowDefinition, next: boolean) => {
    if (!writable) return;
    void setWorkflowActiveVersion(def.id, next).then(() => reload());
  };

  const loadMigrationInstance = async () => {
    try {
      const [instance, approvalList, timerList] = await Promise.all([
        fetchWorkflowInstance(migrationObjectType, migrationObjectId.trim()),
        fetchWorkflowApprovals(migrationObjectType, migrationObjectId.trim()),
        fetchWorkflowTimers(migrationObjectType, migrationObjectId.trim()),
      ]);
      setMigrationInstance(instance);
      setApprovalItems(approvalList);
      setTimerItems(timerList);
      setMigrationTarget(String(instance.definitionVersion));
    } catch {
      setMigrationInstance(null);
      error(t('workflowAdmin.migrationLoadFailed'));
    }
  };

  const requestApproval = async () => {
    if (!migrationInstance || !approvalTransition.trim()) return;
    try {
      await requestWorkflowApproval(migrationInstance.objectType, migrationInstance.objectId, approvalTransition.trim());
      setApprovalItems(await fetchWorkflowApprovals(migrationInstance.objectType, migrationInstance.objectId));
      success(t('workflowAdmin.approvalRequested'));
    } catch { error(t('workflowAdmin.approvalFailed')); }
  };

  const voteApproval = async (id: string, decision: 'APPROVED' | 'REJECTED') => {
    try {
      const changed = await voteWorkflowApproval(id, decision);
      setApprovalItems((items) => items.map((item) => item.id === id ? changed : item));
    } catch { error(t('workflowAdmin.approvalFailed')); }
  };

  const migrateInstance = async () => {
    if (!migrationInstance) return;
    try {
      const migrated = await migrateWorkflowInstance(migrationInstance, Number(migrationTarget));
      setMigrationInstance(migrated);
      success(t('workflowAdmin.migrationDone'));
    } catch {
      error(t('workflowAdmin.migrationFailed'));
    }
  };

  if (loadError) {
    return (
      <section className="page page--workflow">
        <div className="page-head">
          <div>
            <h1>{t('workflowAdmin.title')}</h1>
            <p className="page-subtitle">{t('workflowAdmin.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={() => void reload()} />
      </section>
    );
  }

  return (
    <section className="page page--workflow">
      <div className="page-head">
        <div>
          <h1>{t('workflowAdmin.title')}</h1>
          <p className="page-subtitle">{t('workflowAdmin.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            <Workflow size={14} aria-hidden />
            {t('workflowAdmin.defCount', { n: defs.length })}
          </span>
          <span className="chip chip--muted">
            {t('workflowAdmin.objectCount', { n: objectKeys })}
          </span>
          <span className="chip chip--muted">
            {t('workflowAdmin.activeCount', { n: activeCount })}
          </span>
          <span className={`chip${liveMode ? '' : ' chip--muted'}`}>
            {liveMode ? t('settings.apiModeLive') : t('workflowAdmin.mockHint')}
          </span>
        </div>
      </div>

      {liveMode && writable && (
        <div className="panel mb-4">
          <h2>{t('workflowAdmin.migrationTitle')}</h2>
          <p className="page-subtitle">{t('workflowAdmin.migrationHint')}</p>
          <div className="form-grid mt-3">
            <Select id="workflow-migration-type" label={t('workflowAdmin.objectType')}
              options={[...new Set(defs.map((d) => d.objectKey))].map((value) => ({ value, label: value }))}
              value={migrationObjectType} onChange={(e) => { setMigrationObjectType(e.target.value); setMigrationInstance(null); }} />
            <Input label={t('workflowAdmin.objectId')} value={migrationObjectId}
              onChange={(e) => { setMigrationObjectId(e.target.value); setMigrationInstance(null); }} />
            <Button disabled={!migrationObjectId.trim()} onClick={() => void loadMigrationInstance()}>
              {t('workflowAdmin.loadInstance')}
            </Button>
            {migrationInstance && <>
              <span className="chip">{migrationInstance.state} · v{migrationInstance.definitionVersion} · #{migrationInstance.version}</span>
              <Select id="workflow-migration-target" label={t('workflowAdmin.targetVersion')}
                options={defs.filter((d) => d.objectKey === migrationInstance.objectType)
                  .map((d) => ({ value: String(d.version), label: `v${d.version}${d.active ? ' · active' : ''}` }))}
                value={migrationTarget} onChange={(e) => setMigrationTarget(e.target.value)} />
              <Button disabled={!migrationTarget || Number(migrationTarget) === migrationInstance.definitionVersion}
                onClick={() => void migrateInstance()}>{t('workflowAdmin.migrate')}</Button>
              <Input label={t('workflowAdmin.transitionKey')} value={approvalTransition}
                onChange={(e) => setApprovalTransition(e.target.value)} />
              <Button disabled={!approvalTransition.trim()} onClick={() => void requestApproval()}>
                {t('workflowAdmin.requestApproval')}
              </Button>
            </>}
          </div>
          {approvalItems.length > 0 && <div className="data-table-wrap mt-3"><table className="data-table data-table--dense">
            <thead><tr><th>{t('workflowAdmin.transitionKey')}</th><th>{t('workflowAdmin.approvalMode')}</th>
              <th>{t('workflowAdmin.colStatus')}</th><th>{t('workflowAdmin.approvers')}</th><th /></tr></thead>
            <tbody>{approvalItems.map((approval) => <tr key={approval.id}>
              <td><code>{approval.transitionKey}</code></td><td>{approval.mode}{approval.quorum ? ` · ${approval.quorum}` : ''}</td>
              <td><Badge tone={approval.status === 'APPROVED' ? 'mint' : approval.status === 'REJECTED' ? 'rose' : 'neutral'}>{approval.status}</Badge></td>
              <td>{approval.votes.map((vote) => `${vote.voterId}: ${vote.decision ?? 'PENDING'}`).join(', ')}</td>
              <td>{approval.status === 'PENDING' && <><Button size="sm" onClick={() => void voteApproval(approval.id, 'APPROVED')}>{t('workflowAdmin.approve')}</Button>{' '}<Button size="sm" variant="danger" onClick={() => void voteApproval(approval.id, 'REJECTED')}>{t('workflowAdmin.reject')}</Button></>}</td>
            </tr>)}</tbody>
          </table></div>}
          {timerItems.length > 0 && <div className="data-table-wrap mt-3"><table className="data-table data-table--dense">
            <thead><tr><th>{t('workflowAdmin.transitionKey')}</th><th>{t('workflowAdmin.timerDue')}</th>
              <th>{t('workflowAdmin.colStatus')}</th><th>{t('workflowAdmin.timerAttempts')}</th><th>{t('workflowAdmin.timerError')}</th></tr></thead>
            <tbody>{timerItems.map((timer) => <tr key={timer.id}>
              <td><code>{timer.transitionKey}</code></td><td>{new Date(timer.dueAt).toLocaleString()}</td>
              <td><Badge tone={timer.status === 'COMPLETED' ? 'mint' : timer.status === 'DEAD' ? 'rose' : 'neutral'}>{timer.status}</Badge></td>
              <td>{timer.attempts}/{timer.maxAttempts}</td><td>{timer.lastError ?? '—'}</td>
            </tr>)}</tbody>
          </table></div>}
        </div>
      )}

      <div className="workflow-admin-layout">
        <aside className="panel workflow-admin-list" aria-label={t('workflowAdmin.definitions')}>
          <div className="workflow-admin-list__head">
            <GitBranch size={16} aria-hidden />
            <h2>{t('workflowAdmin.definitions')}</h2>
          </div>
          {defs.length === 0 ? (
            <EmptyState
              title={t('workflowAdmin.emptyTitle')}
              description={t('workflowAdmin.emptyHint')}
            />
          ) : (
            <ul className="workflow-admin-list__items">
              {defs.map((def) => {
                const isSel = selected?.id === def.id;
                return (
                  <li key={def.id}>
                    <button
                      type="button"
                      className={`workflow-admin-btn${isSel ? ' is-active' : ''}`}
                      onClick={() => setSelectedId(def.id)}
                      aria-current={isSel ? 'true' : undefined}
                    >
                      <span className="workflow-admin-btn__row">
                        <span className="workflow-admin-btn__key mono">{def.objectKey}</span>
                        <Badge tone={def.active ? 'mint' : 'neutral'} dot>
                          {def.active
                            ? t('workflowAdmin.statusActive')
                            : t('workflowAdmin.statusInactive')}
                        </Badge>
                      </span>
                      <span className="workflow-admin-btn__name">
                        {def.name ?? def.objectKey}
                      </span>
                      <span className="workflow-admin-btn__meta mono">
                        v{def.version} · {def.states.length} {t('workflowAdmin.statesShort')} ·{' '}
                        {def.transitions.length} {t('workflowAdmin.transShort')}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </aside>

        <div className="panel panel--flush workflow-admin-detail">
          {!selected ? (
            <div className="workflow-admin-detail__empty">
              <EmptyState
                title={t('workflowAdmin.selectTitle')}
                description={t('workflowAdmin.selectHint')}
              />
            </div>
          ) : (
            <>
              <div className="workflow-admin-detail__head">
                <div>
                  <p className="workflow-admin-detail__kicker">
                    {t('workflowAdmin.definition')}
                  </p>
                  <h2>
                    <code className="mono">{selected.objectKey}</code>
                    <span className="workflow-admin-detail__label">
                      {selected.name ?? selected.objectKey}
                    </span>
                  </h2>
                  {selected.description && (
                    <p className="workflow-admin-detail__desc">{selected.description}</p>
                  )}
                </div>
                <div className="workflow-admin-detail__actions">
                  <div className="workflow-admin-detail__badges">
                    <Badge>{t('workflowAdmin.version', { n: selected.version })}</Badge>
                    <Badge tone={selected.active ? 'mint' : 'neutral'} dot>
                      {selected.active
                        ? t('workflowAdmin.statusActive')
                        : t('workflowAdmin.statusInactive')}
                    </Badge>
                    <Badge tone="neutral">
                      {t('workflowAdmin.initialState')}:{' '}
                      <code className="mono">{selected.initialState}</code>
                    </Badge>
                  </div>
                  <Toggle
                    id={`wf-active-${selected.id}`}
                    checked={selected.active}
                    disabled={!writable}
                    onChange={(next) => handleActiveToggle(selected, next)}
                    label={
                      selected.active
                        ? t('workflowAdmin.deactivate')
                        : t('workflowAdmin.activate')
                    }
                    description={t('workflowAdmin.toggleHint')}
                  />
                </div>
              </div>

              <div className="workflow-admin-states">
                <div className="workflow-admin-states__head">
                  <Workflow size={15} aria-hidden />
                  <h3>{t('workflowAdmin.states')}</h3>
                  <span className="workflow-admin-states__count">{selected.states.length}</span>
                </div>
                {selected.states.length === 0 ? (
                  <EmptyState
                    title={t('workflowAdmin.statesEmptyTitle')}
                    description={t('workflowAdmin.statesEmptyHint')}
                  />
                ) : (
                  <ol
                    className="workflow-admin-states__track"
                    aria-label={t('workflowAdmin.states')}
                  >
                    {selected.states.map((state, i) => {
                      const isInitial = state === selected.initialState;
                      return (
                        <li key={state} className="workflow-admin-states__step">
                          {i > 0 && (
                            <span className="workflow-admin-states__connector" aria-hidden />
                          )}
                          <span
                            className={`workflow-admin-states__pill${
                              isInitial ? ' is-initial' : ''
                            }`}
                            title={
                              isInitial ? t('workflowAdmin.initialState') : undefined
                            }
                          >
                            <code>{state}</code>
                            {isInitial && (
                              <em className="workflow-admin-states__initial-mark">
                                {t('workflowAdmin.initialShort')}
                              </em>
                            )}
                          </span>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </div>

              <div className="workflow-admin-section-label">
                <h3>{t('workflowAdmin.transitions')}</h3>
                <span className="chip chip--muted mono">
                  {selected.transitions.length}
                </span>
              </div>

              {selected.transitions.length === 0 ? (
                <div className="workflow-admin-detail__empty workflow-admin-detail__empty--inline">
                  <EmptyState
                    title={t('workflowAdmin.transEmptyTitle')}
                    description={t('workflowAdmin.transEmptyHint')}
                  />
                </div>
              ) : (
                <div className="data-table-wrap data-table-wrap--dense">
                  <table className="data-table data-table--dense">
                    <thead>
                      <tr>
                        <th scope="col">{t('workflowAdmin.colKey')}</th>
                        <th scope="col">{t('workflowAdmin.colFrom')}</th>
                        <th scope="col">{t('workflowAdmin.colTo')}</th>
                        <th scope="col">{t('workflowAdmin.colRequiredFields')}</th>
                        <th scope="col">{t('workflowAdmin.colPermissions')}</th>
                        <th scope="col">{t('workflowAdmin.conditions')}</th>
                        <th scope="col">{t('workflowAdmin.approvalMode')}</th>
                        <th scope="col">{t('workflowAdmin.timer')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selected.transitions.map((tr) => (
                        <tr key={tr.key}>
                          <td>
                            <b className="mono">{tr.key}</b>
                          </td>
                          <td>
                            <code className="meta-type-pill mono">{tr.from}</code>
                          </td>
                          <td>
                            <code className="meta-type-pill mono">{tr.to}</code>
                          </td>
                          <td className="workflow-admin-enums">
                            {tr.requiredFields.length
                              ? tr.requiredFields.map((f) => (
                                  <code key={f} className="meta-type-pill mono">
                                    {f}
                                  </code>
                                ))
                              : '—'}
                          </td>
                          <td className="workflow-admin-enums">
                            {tr.requiredPermissions.length
                              ? tr.requiredPermissions.map((p) => (
                                  <code key={p} className="meta-type-pill mono">
                                    {p}
                                  </code>
                                ))
                              : '—'}
                          </td>
                          <td>{tr.conditions?.length
                            ? tr.conditions.map((condition) => `${condition.field} ${condition.operator} ${JSON.stringify(condition.value)}`).join(', ')
                            : '—'}</td>
                          <td>{tr.approval ? `${tr.approval.mode} · ${tr.approval.voterRoles.join(', ')}${tr.approval.quorum ? ` · ${tr.approval.quorum}` : ''}` : '—'}</td>
                          <td>{tr.timer ? `${tr.timer.delaySeconds}s · ${tr.timer.maxAttempts}×` : '—'}</td>
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
    </section>
  );
}

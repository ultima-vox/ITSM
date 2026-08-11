import { useCallback, useEffect, useMemo, useState } from 'react';
import { GitBranch, Workflow } from 'lucide-react';
import { useT } from '@/i18n';
import {
  fetchWorkflowDefinitions,
  setWorkflowActiveVersion,
  subscribeWorkflowDefinitions,
  isMockMode,
  workflowDefinitionsWritable,
} from '@/api';
import type { WorkflowDefinition } from '@/types';
import { Badge, EmptyState, ErrorState, Toggle } from '@/components/ui';

export function WorkflowPage() {
  const t = useT();
  const writable = workflowDefinitionsWritable();
  const liveMode = !isMockMode();
  const [defs, setDefs] = useState<WorkflowDefinition[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);

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

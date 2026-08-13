import { useCallback, useEffect, useMemo, useState } from 'react';
import { Bolt, Filter, History, Play, Zap, X } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import {
  automationRulesWritable,
  fetchAutomationRules,
  fetchAutomationExecutions,
  setAutomationRuleEnabled,
  subscribeAutomationRules,
  isMockMode,
  saveAutomationRule,
} from '@/api';
import type { AutomationAction, AutomationExecution, AutomationRule } from '@/types';
import { Badge, Button, EmptyState, ErrorState, Input, Modal, Skeleton, Toggle } from '@/components/ui';
import { formatDateTime, formatRelative } from '@/lib/format';
import { useToast } from '@/hooks/useToast';

function formatActionParams(action: AutomationAction): string {
  const entries = Object.entries(action.parameters ?? {});
  if (!entries.length) return '—';
  return entries.map(([k, v]) => `${k}: ${String(v)}`).join(' · ');
}

function executionTone(status: AutomationExecution['status']): 'mint' | 'rose' | 'amber' | 'neutral' {
  switch (status) {
    case 'SUCCEEDED':
      return 'mint';
    case 'FAILED':
      return 'rose';
    case 'STARTED':
      return 'amber';
    default:
      return 'neutral';
  }
}

function executionDetail(execution: AutomationExecution): string {
  const entries = Object.entries(execution.details ?? {}).slice(0, 3);
  if (!entries.length) return '—';
  return entries.map(([k, v]) => `${k}: ${String(v)}`).join(' · ');
}

export function AutomationPage() {
  const t = useT();
  const { locale } = useI18n();
  const writable = automationRulesWritable();
  const liveMode = !isMockMode();
  const [tab, setTab] = useState<'rules' | 'executions'>('rules');
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [executions, setExecutions] = useState<AutomationExecution[]>([]);
  const [execLoading, setExecLoading] = useState(false);
  const [execError, setExecError] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [designerOpen, setDesignerOpen] = useState(false);
  const [draftKey, setDraftKey] = useState('');
  const [draftName, setDraftName] = useState('');
  const [draftEvent, setDraftEvent] = useState('work-item.created');
  const [draftConditions, setDraftConditions] = useState('[]');
  const [draftActions, setDraftActions] = useState('[{"type":"log","parameters":{}}]');
  const [editing, setEditing] = useState<AutomationRule | null>(null);
  const [saving, setSaving] = useState(false);
  const { success, error: toastError } = useToast();

  const reload = useCallback(async () => {
    try {
      const list = await fetchAutomationRules();
      setRules(list);
      setLoadError(false);
    } catch {
      setLoadError(true);
    }
  }, []);

  const reloadExecutions = useCallback(async () => {
    setExecLoading(true);
    setExecError(false);
    try {
      const list = await fetchAutomationExecutions({ limit: 100 });
      setExecutions(list);
    } catch {
      setExecError(true);
    } finally {
      setExecLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
    return subscribeAutomationRules(() => {
      void reload();
    });
  }, [reload]);

  useEffect(() => {
    if (tab !== 'executions') return;
    let cancelled = false;
    setExecLoading(true);
    setExecError(false);
    fetchAutomationExecutions({ limit: 100 })
      .then((list) => {
        if (!cancelled) setExecutions(list);
      })
      .catch(() => {
        if (!cancelled) setExecError(true);
      })
      .finally(() => {
        if (!cancelled) setExecLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tab]);

  const selected: AutomationRule | null = useMemo(() => {
    if (!rules.length) return null;
    const id = selectedId ?? rules[0]?.id ?? null;
    return rules.find((r) => r.id === id) ?? rules[0] ?? null;
  }, [rules, selectedId]);

  const enabledCount = rules.filter((r) => r.enabled).length;

  const handleToggle = (rule: AutomationRule, next: boolean) => {
    if (!writable) return;
    void setAutomationRuleEnabled(rule.id, next).then(() => reload());
  };

  const openDesigner = (source?: AutomationRule) => {
    setEditing(source ?? null);
    setDraftKey(source?.ruleKey ?? 'custom.rule');
    setDraftName(source?.name ?? 'New automation rule');
    setDraftEvent(source?.trigger.eventType ?? 'work-item.created');
    setDraftConditions(JSON.stringify(source?.conditions ?? [], null, 2));
    setDraftActions(JSON.stringify(source?.actions ?? [{ type: 'log', parameters: {} }], null, 2));
    setDesignerOpen(true);
  };

  const saveRule = async () => {
    setSaving(true);
    try {
      const saved = await saveAutomationRule({ ruleKey: draftKey.trim(), name: draftName.trim(),
        version: editing?.version ?? 1, enabled: editing?.enabled ?? false,
        trigger: { eventType: draftEvent.trim() },
        conditions: JSON.parse(draftConditions) as AutomationRule['conditions'],
        actions: JSON.parse(draftActions) as AutomationRule['actions'] }, editing?.id);
      setDesignerOpen(false);
      setSelectedId(saved.id);
      await reload();
      success(t('automation.saved'));
    } catch { toastError(t('automation.saveFailed')); }
    finally { setSaving(false); }
  };

  if (loadError) {
    return (
      <section className="page page--automation">
        <div className="page-head">
          <div>
            <h1>{t('automation.title')}</h1>
            <p className="page-subtitle">{t('automation.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={() => void reload()} />
      </section>
    );
  }

  return (
    <section className="page page--automation">
      <div className="page-head">
        <div>
          <h1>{t('automation.title')}</h1>
          <p className="page-subtitle">{t('automation.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <div className="filter-chips" role="group" aria-label={t('automation.tabs')}>
            <button
              type="button"
              className={`chip chip--toggle${tab === 'rules' ? ' is-on' : ''}`}
              onClick={() => setTab('rules')}
              aria-pressed={tab === 'rules'}
            >
              <Bolt size={14} aria-hidden />
              {t('automation.rules')}
            </button>
            <button
              type="button"
              className={`chip chip--toggle${tab === 'executions' ? ' is-on' : ''}`}
              onClick={() => setTab('executions')}
              aria-pressed={tab === 'executions'}
            >
              <History size={14} aria-hidden />
              {t('automation.executions')}
            </button>
          </div>
          {tab === 'rules' && liveMode && <Button size="sm" onClick={() => openDesigner()}>{t('automation.newRule')}</Button>}
          {tab === 'rules' && (
            <>
              <span className="chip">
                <Zap size={14} aria-hidden />
                {t('automation.ruleCount', { n: rules.length })}
              </span>
              <span className="chip chip--muted">
                {t('automation.enabledCount', { n: enabledCount })}
              </span>
            </>
          )}
          <span className={`chip${liveMode ? '' : ' chip--muted'}`}>
            {liveMode ? t('settings.apiModeLive') : t('automation.mockHint')}
          </span>
        </div>
      </div>

      {tab === 'executions' && (
        <div className="panel audit-table-wrap">
          <div className="page-subtitle automation-exec__head">
            <History size={14} aria-hidden />
            {t('automation.executionsSubtitle')}
          </div>
          {execError && <ErrorState onRetry={() => void reloadExecutions()} />}
          {execLoading && !execError && (
            <div aria-busy="true">
              <Skeleton height={36} />
              <Skeleton height={36} className="mt-2" />
              <Skeleton height={36} className="mt-2" />
              <Skeleton height={36} className="mt-2" />
            </div>
          )}
          {!execLoading && !execError && executions.length === 0 && (
            <EmptyState
              title={t('automation.executionsEmptyTitle')}
              description={t('automation.executionsEmptyHint')}
              icon={<History size={22} />}
            />
          )}
          {!execLoading && !execError && executions.length > 0 && (
            <table className="data-table data-table--dense">
              <thead>
                <tr>
                  <th scope="col">{t('automation.execColTime')}</th>
                  <th scope="col">{t('automation.execColRule')}</th>
                  <th scope="col">{t('automation.execColAction')}</th>
                  <th scope="col">{t('automation.execColStatus')}</th>
                  <th scope="col">{t('automation.execColDetail')}</th>
                </tr>
              </thead>
              <tbody>
                {executions.map((execution) => (
                  <tr key={execution.id}>
                    <td>
                      <time dateTime={execution.createdAt} title={formatDateTime(execution.createdAt, locale)}>
                        {formatRelative(execution.createdAt, t)}
                      </time>
                    </td>
                    <td><code className="mono">{execution.ruleKey}</code></td>
                    <td><code className="meta-type-pill mono">{execution.actionType}</code></td>
                    <td>
                      <Badge tone={executionTone(execution.status)} dot>
                        {execution.status}
                      </Badge>
                    </td>
                    <td className="muted">{executionDetail(execution)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === 'rules' && (
      <div className="automation-layout">
        <aside className="panel automation-rules" aria-label={t('automation.rules')}>
          <div className="automation-rules__head">
            <Bolt size={16} aria-hidden />
            <h2>{t('automation.rules')}</h2>
          </div>          {rules.length === 0 ? (
            <EmptyState
              title={t('automation.emptyTitle')}
              description={t('automation.emptyHint')}
            />
          ) : (
            <ul className="automation-rules__list">
              {rules.map((rule) => {
                const active = selected?.id === rule.id;
                return (
                  <li key={rule.id}>
                    <button
                      type="button"
                      className={`automation-rule-btn${active ? ' is-active' : ''}`}
                      onClick={() => setSelectedId(rule.id)}
                      aria-current={active ? 'true' : undefined}
                    >
                      <span className="automation-rule-btn__row">
                        <span className="automation-rule-btn__key mono">{rule.ruleKey}</span>
                        <Badge tone={rule.enabled ? 'mint' : 'neutral'} dot>
                          {rule.enabled
                            ? t('automation.statusEnabled')
                            : t('automation.statusDisabled')}
                        </Badge>
                      </span>
                      <span className="automation-rule-btn__name">{rule.name}</span>
                      <span className="automation-rule-btn__meta mono">
                        WHEN {rule.trigger.eventType}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </aside>

        <div className="panel panel--flush automation-detail">
          {!selected ? (
            <div className="automation-detail__empty">
              <EmptyState
                title={t('automation.selectTitle')}
                description={t('automation.selectHint')}
              />
            </div>
          ) : (
            <>
              <div className="automation-detail__head">
                <div>
                  <p className="automation-detail__kicker">{t('automation.ruleDefinition')}</p>
                  <h2>
                    <code className="mono">{selected.ruleKey}</code>
                    <span className="automation-detail__label">{selected.name}</span>
                  </h2>
                  {selected.description && (
                    <p className="automation-detail__desc">{selected.description}</p>
                  )}
                </div>
                <div className="automation-detail__actions">
                  {liveMode && <Button variant="secondary" size="sm" onClick={() => openDesigner(selected)}>
                    {t('app.edit')}
                  </Button>}
                  <Badge tone="neutral">v{selected.version}</Badge>
                  <Badge tone={selected.enabled ? 'mint' : 'neutral'} dot>
                    {selected.enabled
                      ? t('automation.statusEnabled')
                      : t('automation.statusDisabled')}
                  </Badge>
                  <Toggle
                    id={`auto-enable-${selected.id}`}
                    checked={selected.enabled}
                    onChange={(next) => handleToggle(selected, next)}
                    disabled={!writable}
                    label={
                      selected.enabled
                        ? t('automation.disable')
                        : t('automation.enable')
                    }
                    description={liveMode ? t('automation.toggleHint') : t('automation.mockHint')}
                  />
                </div>
              </div>

              <div className="automation-flow" aria-label={t('automation.flow')}>
                <section className="automation-block">
                  <header className="automation-block__head">
                    <Play size={14} aria-hidden />
                    <h3>{t('automation.when')}</h3>
                  </header>
                  <div className="automation-block__body">
                    <code className="meta-type-pill mono">{selected.trigger.eventType}</code>
                    <p className="automation-block__hint">{t('automation.eventHint')}</p>
                  </div>
                </section>

                <section className="automation-block">
                  <header className="automation-block__head">
                    <Filter size={14} aria-hidden />
                    <h3>{t('automation.if')}</h3>
                    <span className="automation-block__count">
                      {selected.conditions.length}
                    </span>
                  </header>
                  <div className="automation-block__body">
                    {selected.conditions.length === 0 ? (
                      <p className="automation-block__empty">{t('automation.noConditions')}</p>
                    ) : (
                      <ul className="automation-cond-list">
                        {selected.conditions.map((c, i) => (
                          <li key={`${c.field}-${i}`} className="automation-cond">
                            <span className="mono automation-cond__field">{c.field}</span>
                            <code className="meta-type-pill">{c.operator}</code>
                            <span className="mono automation-cond__value">{c.value}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </section>

                <section className="automation-block">
                  <header className="automation-block__head">
                    <Zap size={14} aria-hidden />
                    <h3>{t('automation.then')}</h3>
                    <span className="automation-block__count">
                      {selected.actions.length}
                    </span>
                  </header>
                  <div className="automation-block__body">
                    {selected.actions.length === 0 ? (
                      <p className="automation-block__empty">{t('automation.noActions')}</p>
                    ) : (
                      <ul className="automation-action-list">
                        {selected.actions.map((a, i) => (
                          <li key={`${a.type}-${i}`} className="automation-action">
                            <code className="meta-type-pill mono">{a.type}</code>
                            <span className="automation-action__params">
                              {formatActionParams(a)}
                            </span>
                            <pre className="automation-json mono" tabIndex={0}>
                              {JSON.stringify(a.parameters, null, 2)}
                            </pre>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </section>
              </div>
            </>
          )}
        </div>
      </div>
      )}

      <Modal open={designerOpen} onClose={() => setDesignerOpen(false)} size="lg" labelledBy="automation-designer-title">
        <div className="dialog-head"><div><p className="eyebrow">{t('automation.designer')}</p>
          <h2 id="automation-designer-title">{editing ? t('automation.editRule') : t('automation.newRule')}</h2></div>
          <button type="button" className="icon-btn" aria-label={t('app.close')} onClick={() => setDesignerOpen(false)}><X size={18} /></button>
        </div>
        <div className="form-grid">
          <Input label={t('automation.key')} value={draftKey} disabled={Boolean(editing)} onChange={(event) => setDraftKey(event.target.value)} />
          <Input label={t('automation.name')} value={draftName} onChange={(event) => setDraftName(event.target.value)} />
          <Input label={t('automation.eventType')} value={draftEvent} onChange={(event) => setDraftEvent(event.target.value)} />
        </div>
        <label className="field"><span className="field__label">{t('automation.conditionsJson')}</span>
          <textarea className="input mono" rows={8} value={draftConditions} onChange={(event) => setDraftConditions(event.target.value)} /></label>
        <label className="field"><span className="field__label">{t('automation.actionsJson')}</span>
          <textarea className="input mono" rows={8} value={draftActions} onChange={(event) => setDraftActions(event.target.value)} /></label>
        <p className="panel-hint">{t('automation.designerHint')}</p>
        <div className="dialog-actions"><Button variant="secondary" onClick={() => setDesignerOpen(false)}>{t('app.cancel')}</Button>
          <Button disabled={saving} onClick={() => void saveRule()}>{t('app.save')}</Button></div>
      </Modal>
    </section>
  );
}

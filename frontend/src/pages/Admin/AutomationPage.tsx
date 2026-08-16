import { useCallback, useEffect, useMemo, useState } from 'react';
import { Bolt, Filter, Play, Zap } from 'lucide-react';
import { useT } from '@/i18n';
import {
  automationRulesWritable,
  fetchAutomationRules,
  setAutomationRuleEnabled,
  subscribeAutomationRules,
  useMock,
} from '@/api';
import type { AutomationAction, AutomationRule } from '@/types';
import { Badge, EmptyState, ErrorState, Toggle } from '@/components/ui';

function formatActionParams(action: AutomationAction): string {
  const entries = Object.entries(action.parameters ?? {});
  if (!entries.length) return '—';
  return entries.map(([k, v]) => `${k}: ${String(v)}`).join(' · ');
}

export function AutomationPage() {
  const t = useT();
  const writable = automationRulesWritable();
  const liveMode = !useMock();
  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);

  const reload = useCallback(async () => {
    try {
      const list = await fetchAutomationRules();
      setRules(list);
      setLoadError(false);
    } catch {
      setLoadError(true);
    }
  }, []);

  useEffect(() => {
    void reload();
    return subscribeAutomationRules(() => {
      void reload();
    });
  }, [reload]);

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
          <span className="chip">
            <Zap size={14} aria-hidden />
            {t('automation.ruleCount', { n: rules.length })}
          </span>
          <span className="chip chip--muted">
            {t('automation.enabledCount', { n: enabledCount })}
          </span>
          <span className={`chip${liveMode ? '' : ' chip--muted'}`}>
            {liveMode ? t('settings.apiModeLive') : t('automation.mockHint')}
          </span>
        </div>
      </div>

      <div className="automation-layout">
        <aside className="panel automation-rules" aria-label={t('automation.rules')}>
          <div className="automation-rules__head">
            <Bolt size={16} aria-hidden />
            <h2>{t('automation.rules')}</h2>
          </div>
          {rules.length === 0 ? (
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
                    description={t('automation.toggleHint')}
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
    </section>
  );
}

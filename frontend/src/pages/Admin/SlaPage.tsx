import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarClock, Clock, Save, Timer } from 'lucide-react';
import { useT } from '@/i18n';
import { useToast } from '@/hooks/useToast';
import {
  fetchSlaPolicies,
  fetchWorkingCalendars,
  updateWorkingCalendar,
  setSlaPolicyEnabled,
  slaPoliciesWritable,
  subscribeSlaPolicies,
  updateSlaPolicyTargets,
  isMockMode,
} from '@/api';
import { reseedOpenWorkItemSlaFromPolicies } from '@/mock/store';
import type { SlaPolicy, SlaTarget, WorkingCalendarMock } from '@/types';
import { Badge, Button, EmptyState, ErrorState, Input } from '@/components/ui';

function dayLabel(day: string, t: (k: string) => string): string {
  const key = `slaAdmin.day.${day}`;
  const translated = t(key);
  return translated === key ? day : translated;
}

const CALENDAR_DAYS = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
];

export function SlaPage() {
  const t = useT();
  const { success } = useToast();
  const writable = slaPoliciesWritable();
  const liveMode = !isMockMode();
  const [policies, setPolicies] = useState<SlaPolicy[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [draftTargets, setDraftTargets] = useState<SlaTarget[]>([]);
  const [dirty, setDirty] = useState(false);
  const [calendars, setCalendars] = useState<WorkingCalendarMock[]>([]);
  const [calendarDraft, setCalendarDraft] = useState<WorkingCalendarMock | null>(null);
  const [calendarDirty, setCalendarDirty] = useState(false);

  const reload = useCallback(async () => {
    try {
      const [list, calendarList] = await Promise.all([
        fetchSlaPolicies(),
        fetchWorkingCalendars(),
      ]);
      setPolicies(list);
      setCalendars(calendarList);
      setLoadError(false);
    } catch {
      setLoadError(true);
    }
  }, []);

  useEffect(() => {
    void reload();
    return subscribeSlaPolicies(() => {
      void reload();
    });
  }, [reload]);

  const selected: SlaPolicy | null = useMemo(() => {
    if (!policies.length) return null;
    const id = selectedId ?? policies[0]?.id ?? null;
    return policies.find((p) => p.id === id) ?? policies[0] ?? null;
  }, [policies, selectedId]);

  // Sync draft when selection changes or store reloads this policy
  const selectedKey = selected
    ? `${selected.id}:${selected.targets.map((tg) => `${tg.metric}|${tg.condition}|${tg.targetHours}|${tg.warningBeforeHours}`).join(';')}`
    : '';

  useEffect(() => {
    if (!selected) {
      setDraftTargets([]);
      setDirty(false);
      return;
    }
    setDraftTargets(selected.targets.map((tg) => ({ ...tg })));
    setDirty(false);
    // selectedKey fingerprints stored targets so in-progress edits are not clobbered
    // until the session store actually changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional fingerprint
  }, [selectedKey]);

  const calendar: WorkingCalendarMock | null = selected
    ? calendars.find((candidate) => candidate.key === selected.calendarKey) ?? calendars[0] ?? null
    : calendars[0] ?? null;

  const calendarFingerprint = calendar
    ? `${calendar.id}:${calendar.version}:${calendar.zone}:${calendar.startsAt}:${calendar.endsAt}`
    : '';
  useEffect(() => {
    setCalendarDraft(calendar ? { ...calendar } : null);
    setCalendarDirty(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- server version fingerprints saved state
  }, [calendarFingerprint]);

  const handleCalendarSave = async () => {
    if (!calendarDraft?.id || !writable) return;
    try {
      const updated = await updateWorkingCalendar(calendarDraft);
      setCalendars((current) => current.map((row) => row.id === updated.id ? updated : row));
      setCalendarDirty(false);
      success(t('slaAdmin.savedReseedToast'));
    } catch {
      setLoadError(true);
    }
  };

  const handleTargetChange = (
    index: number,
    field: 'targetHours' | 'warningBeforeHours',
    raw: string,
  ) => {
    const num = raw === '' ? 0 : Number(raw);
    setDraftTargets((prev) =>
      prev.map((row, i) =>
        i === index
          ? { ...row, [field]: Number.isFinite(num) ? num : 0 }
          : row,
      ),
    );
    setDirty(true);
  };

  const handleSave = async () => {
    if (!selected || !writable) return;
    try {
      const updated = await updateSlaPolicyTargets(selected.id, selected.version, draftTargets);
      if (updated) {
        setDirty(false);
        if (isMockMode()) reseedOpenWorkItemSlaFromPolicies();
        success(t('slaAdmin.savedToast'));
        await reload();
      }
    } catch {
      setLoadError(true);
    }
  };

  const handleToggleEnabled = async () => {
    if (!selected || !writable) return;
    try {
      const updated = await setSlaPolicyEnabled(selected.id, selected.version, !selected.enabled);
      if (updated) {
        if (isMockMode()) reseedOpenWorkItemSlaFromPolicies();
        success(
          updated.enabled
            ? t('slaAdmin.enabledToast')
            : t('slaAdmin.disabledToast'),
        );
        await reload();
      }
    } catch {
      setLoadError(true);
    }
  };

  if (loadError) {
    return (
      <section className="page page--sla">
        <div className="page-head">
          <div>
            <h1>{t('slaAdmin.title')}</h1>
            <p className="page-subtitle">{t('slaAdmin.subtitle')}</p>
          </div>
        </div>
        <ErrorState onRetry={() => void reload()} />
      </section>
    );
  }

  return (
    <section className="page page--sla">
      <div className="page-head">
        <div>
          <h1>{t('slaAdmin.title')}</h1>
          <p className="page-subtitle">{t('slaAdmin.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            <Timer size={14} aria-hidden />
            {t('slaAdmin.policyCount', { n: policies.length })}
          </span>
          <span className={`chip${liveMode ? '' : ' chip--muted'}`}>
            {liveMode ? t('settings.apiModeLive') : t('slaAdmin.mockHint')}
          </span>
        </div>
      </div>

      <div className="sla-admin-layout">
        <aside className="panel sla-admin-list" aria-label={t('slaAdmin.policies')}>
          <div className="sla-admin-list__head">
            <Clock size={16} aria-hidden />
            <h2>{t('slaAdmin.policies')}</h2>
          </div>
          {policies.length === 0 ? (
            <EmptyState
              title={t('slaAdmin.emptyTitle')}
              description={t('slaAdmin.emptyHint')}
            />
          ) : (
            <ul className="sla-admin-list__items">
              {policies.map((p) => {
                const isSel = selected?.id === p.id;
                return (
                  <li key={p.id}>
                    <button
                      type="button"
                      className={`sla-admin-btn${isSel ? ' is-active' : ''}`}
                      onClick={() => setSelectedId(p.id)}
                      aria-current={isSel ? 'true' : undefined}
                    >
                      <span className="sla-admin-btn__row">
                        <span className="sla-admin-btn__key mono">{p.key}</span>
                        <Badge tone={p.enabled ? 'mint' : 'neutral'} dot>
                          {p.enabled
                            ? t('slaAdmin.statusEnabled')
                            : t('slaAdmin.statusDisabled')}
                        </Badge>
                      </span>
                      <span className="sla-admin-btn__name">{p.name ?? p.key}</span>
                      <span className="sla-admin-btn__meta mono">
                        {p.targets.length} {t('slaAdmin.targetsShort')} · {p.calendarKey}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </aside>

        <div className="panel panel--flush sla-admin-detail">
          {!selected ? (
            <div className="sla-admin-detail__empty">
              <EmptyState
                title={t('slaAdmin.selectTitle')}
                description={t('slaAdmin.selectHint')}
              />
            </div>
          ) : (
            <>
              <div className="sla-admin-detail__head">
                <div>
                  <p className="sla-admin-detail__kicker">{t('slaAdmin.policyDefinition')}</p>
                  <h2>
                    <code className="mono">{selected.key}</code>
                    <span className="sla-admin-detail__label">
                      {selected.name ?? selected.key}
                    </span>
                  </h2>
                  {selected.description && (
                    <p className="sla-admin-detail__desc">{selected.description}</p>
                  )}
                </div>
                <div className="sla-admin-detail__actions">
                  <button
                    type="button"
                    className={`chip chip--toggle${selected.enabled ? ' is-on' : ''}`}
                    aria-pressed={selected.enabled}
                    onClick={() => void handleToggleEnabled()}
                    title={t('slaAdmin.toggleEnabled')}
                    disabled={!writable}
                  >
                    {selected.enabled
                      ? t('slaAdmin.statusEnabled')
                      : t('slaAdmin.statusDisabled')}
                  </button>
                  {writable && (
                    <Button
                      variant="primary"
                      size="sm"
                      icon={<Save size={14} />}
                      onClick={() => void handleSave()}
                      disabled={!dirty}
                    >
                      {t('slaAdmin.save')}
                    </Button>
                  )}
                </div>
              </div>

              {calendarDraft && (
                <div className="sla-admin-calendar" aria-label={t('slaAdmin.calendar')}>
                  <div className="sla-admin-calendar__head">
                    <CalendarClock size={15} aria-hidden />
                    <h3>{t('slaAdmin.calendar')}</h3>
                    <code className="mono sla-admin-calendar__key">{calendarDraft.key}</code>
                    {liveMode && calendarDraft.id && (
                      <Button
                        variant="secondary"
                        size="sm"
                        icon={<Save size={14} />}
                        onClick={() => void handleCalendarSave()}
                        disabled={!calendarDirty || !writable}
                      >
                        {t('slaAdmin.save')}
                      </Button>
                    )}
                  </div>
                  <div className="sla-admin-calendar__grid">
                    <div className="sla-admin-calendar__cell">
                      <span className="sla-admin-calendar__label">{t('slaAdmin.zone')}</span>
                      <Input
                        name="sla-calendar-zone"
                        value={calendarDraft.zone}
                        disabled={!liveMode || !writable}
                        onChange={(event) => {
                          setCalendarDraft({ ...calendarDraft, zone: event.target.value });
                          setCalendarDirty(true);
                        }}
                        aria-label={t('slaAdmin.zone')}
                      />
                    </div>
                    <div className="sla-admin-calendar__cell">
                      <span className="sla-admin-calendar__label">{t('slaAdmin.window')}</span>
                      <div className="sla-admin-calendar__days">
                        <Input
                          name="sla-calendar-start"
                          type="time"
                          value={calendarDraft.startsAt}
                          disabled={!liveMode || !writable}
                          onChange={(event) => {
                            setCalendarDraft({ ...calendarDraft, startsAt: event.target.value });
                            setCalendarDirty(true);
                          }}
                          aria-label={t('slaAdmin.window')}
                        />
                        <Input
                          name="sla-calendar-end"
                          type="time"
                          value={calendarDraft.endsAt}
                          disabled={!liveMode || !writable}
                          onChange={(event) => {
                            setCalendarDraft({ ...calendarDraft, endsAt: event.target.value });
                            setCalendarDirty(true);
                          }}
                          aria-label={t('slaAdmin.window')}
                        />
                      </div>
                    </div>
                    <div className="sla-admin-calendar__cell sla-admin-calendar__cell--wide">
                      <span className="sla-admin-calendar__label">
                        {t('slaAdmin.workingDays')}
                      </span>
                      <div className="sla-admin-calendar__days">
                        {CALENDAR_DAYS.map((day) => {
                          const enabled = calendarDraft.workingDays.includes(day);
                          return (
                            <button
                              key={day}
                              type="button"
                              className={`sla-admin-calendar__day${enabled ? ' is-active' : ''}`}
                              aria-pressed={enabled}
                              disabled={!liveMode || !writable}
                              onClick={() => {
                                const workingDays = enabled
                                  ? calendarDraft.workingDays.filter((value) => value !== day)
                                  : [...calendarDraft.workingDays, day];
                                setCalendarDraft({ ...calendarDraft, workingDays });
                                setCalendarDirty(true);
                              }}
                            >
                              {dayLabel(day, t)}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                  <p className="sla-admin-calendar__note">{t('slaAdmin.calendarNote')}</p>
                </div>
              )}

              <div className="sla-admin-section-label">
                <h3>{t('slaAdmin.targets')}</h3>
                <span className="chip chip--muted">{t('slaAdmin.hoursUnit')}</span>
              </div>

              {selected.pauseStates.length > 0 && (
                <p className="sla-admin-pause">
                  <span className="sla-admin-pause__label">{t('slaAdmin.pauseStates')}:</span>
                  {selected.pauseStates.map((s) => (
                    <code key={s} className="meta-type-pill mono">
                      {s}
                    </code>
                  ))}
                </p>
              )}

              {draftTargets.length === 0 ? (
                <div className="sla-admin-detail__empty sla-admin-detail__empty--inline">
                  <EmptyState
                    title={t('slaAdmin.targetsEmptyTitle')}
                    description={t('slaAdmin.targetsEmptyHint')}
                  />
                </div>
              ) : (
                <div className="data-table-wrap data-table-wrap--dense">
                  <table className="data-table data-table--dense">
                    <thead>
                      <tr>
                        <th scope="col">{t('slaAdmin.colMetric')}</th>
                        <th scope="col">{t('slaAdmin.colCondition')}</th>
                        <th scope="col">{t('slaAdmin.colTargetHours')}</th>
                        <th scope="col">{t('slaAdmin.colWarningHours')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {draftTargets.map((row, i) => (
                        <tr key={`${row.metric}-${row.condition}-${i}`}>
                          <td>
                            <code className="meta-type-pill mono">{row.metric}</code>
                          </td>
                          <td>
                            <code className="mono">{row.condition}</code>
                          </td>
                          <td>
                            <Input
                              name={`sla-target-${i}`}
                              type="number"
                              min={0.01}
                              step={0.01}
                              value={String(row.targetHours)}
                              disabled={!writable}
                              onChange={(e) =>
                                handleTargetChange(i, 'targetHours', e.target.value)
                              }
                              aria-label={t('slaAdmin.colTargetHours')}
                              className="sla-admin-hours-input"
                            />
                          </td>
                          <td>
                            <Input
                              name={`sla-warn-${i}`}
                              type="number"
                              min={0}
                              step={0.01}
                              value={String(row.warningBeforeHours)}
                              disabled={!writable}
                              onChange={(e) =>
                                handleTargetChange(
                                  i,
                                  'warningBeforeHours',
                                  e.target.value,
                                )
                              }
                              aria-label={t('slaAdmin.colWarningHours')}
                              className="sla-admin-hours-input"
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {dirty && (
                <div className="sla-admin-footer">
                  <p className="sla-admin-footer__hint">{t('slaAdmin.unsavedHint')}</p>
                  <Button
                    variant="primary"
                    size="sm"
                    icon={<Save size={14} />}
                    onClick={handleSave}
                  >
                    {t('slaAdmin.save')}
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}

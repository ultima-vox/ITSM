import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, Plus, Trash2, Users } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useToast } from '@/hooks/useToast';
import {
  addOnCallOverride,
  deleteEscalationPolicy,
  deleteOnCallOverride,
  deleteOnCallSchedule,
  fetchEscalationPolicies,
  fetchOnCallNow,
  fetchOnCallOverrides,
  fetchOnCallSchedules,
  formatRotation,
  saveEscalationPolicy,
  saveOnCallSchedule,
} from '@/api/oncall';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  Modal,
  Select,
  Skeleton,
  Toggle,
} from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import type {
  EscalationPolicy,
  EscalationStep,
  OnCallOverride,
  OnCallSchedule,
} from '@/types';

function errorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'body' in err) {
    const body = (err as { body?: { message?: string; detail?: string } }).body;
    if (body?.detail) return body.detail;
    if (body?.message) return body.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

function toLocalInput(iso: string): string {
  const date = new Date(iso);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function OnCallPage() {
  const t = useT();
  const toast = useToast();

  const [schedules, setSchedules] = useState<OnCallSchedule[] | null>(null);
  const [current, setCurrent] = useState<Record<string, string | null>>({});
  const [policies, setPolicies] = useState<EscalationPolicy[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [scheduleForm, setScheduleForm] = useState<OnCallSchedule | 'new' | null>(null);
  const [policyForm, setPolicyForm] = useState<EscalationPolicy | 'new' | null>(null);
  const [overridesFor, setOverridesFor] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [loadedSchedules, loadedPolicies] = await Promise.all([
        fetchOnCallSchedules(),
        fetchEscalationPolicies(),
      ]);
      setSchedules(loadedSchedules);
      setPolicies(loadedPolicies);
      const now = new Date().toISOString();
      const entries = await Promise.all(
        loadedSchedules.map(async (schedule) => {
          const answer = await fetchOnCallNow(schedule.scheduleKey, now);
          return [schedule.scheduleKey, answer.subject] as const;
        }),
      );
      setCurrent(Object.fromEntries(entries));
    } catch (err) {
      toast.error(errorMessage(err, t('oncall.loadFailed')));
    }
    // toast identity is stable for the provider lifetime
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(action: () => Promise<unknown>, successKey: string, fallbackKey: string) {
    setBusy(true);
    try {
      await action();
      toast.success(t(successKey));
      await load();
    } catch (err) {
      toast.error(errorMessage(err, t(fallbackKey)));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="page page--oncall">
      <div className="page-head">
        <div>
          <h1>{t('oncall.title')}</h1>
          <p className="page-subtitle">{t('oncall.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <Button icon={<Plus size={16} />} onClick={() => setScheduleForm('new')}>
            {t('oncall.newSchedule')}
          </Button>
        </div>
      </div>

      <section className="panel">
        <h2>{t('oncall.schedulesTitle')}</h2>
        {!schedules && <Skeleton height={120} />}
        {schedules && schedules.length === 0 && (
          <EmptyState
            title={t('oncall.noSchedules')}
            description={t('oncall.noSchedulesHint')}
            icon={<CalendarClock size={22} />}
            actionLabel={t('oncall.newSchedule')}
            onAction={() => setScheduleForm('new')}
          />
        )}
        {schedules && schedules.length > 0 && (
          <table className="data-table data-table--dense">
            <thead>
              <tr>
                <th scope="col">{t('oncall.colKey')}</th>
                <th scope="col">{t('oncall.colName')}</th>
                <th scope="col">{t('oncall.colRotation')}</th>
                <th scope="col">{t('oncall.colParticipants')}</th>
                <th scope="col">{t('oncall.colOnCallNow')}</th>
                <th scope="col" aria-label={t('app.actions')} />
              </tr>
            </thead>
            <tbody>
              {schedules.map((schedule) => (
                <tr key={schedule.id}>
                  <td>{schedule.scheduleKey}</td>
                  <td>
                    {schedule.name}
                    {!schedule.active && (
                      <Badge tone="neutral" className="ml-2">
                        {t('oncall.inactive')}
                      </Badge>
                    )}
                  </td>
                  <td className="muted">
                    {formatRotation(schedule.rotationHours)} · {schedule.timeZone}
                  </td>
                  <td className="muted">
                    <Users size={14} aria-hidden /> {schedule.participants.join(', ')}
                  </td>
                  <td>
                    {current[schedule.scheduleKey] ? (
                      <Badge tone="mint">{current[schedule.scheduleKey]}</Badge>
                    ) : (
                      <span className="muted">{t('oncall.nobody')}</span>
                    )}
                  </td>
                  <td className="row-actions">
                    <Button variant="ghost" size="sm" onClick={() => setScheduleForm(schedule)}>
                      {t('oncall.edit')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setOverridesFor(schedule.scheduleKey)}
                    >
                      {t('oncall.overrides')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={<Trash2 size={14} />}
                      disabled={busy}
                      onClick={() =>
                        void run(
                          () => deleteOnCallSchedule(schedule.scheduleKey),
                          'oncall.scheduleDeleted',
                          'oncall.saveFailed',
                        )
                      }
                    >
                      {t('oncall.delete')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="panel mt-4">
        <div className="panel__header">
          <h2>{t('oncall.policiesTitle')}</h2>
          <Button variant="secondary" icon={<Plus size={16} />} onClick={() => setPolicyForm('new')}>
            {t('oncall.newPolicy')}
          </Button>
        </div>
        {!policies && <Skeleton height={100} />}
        {policies && policies.length === 0 && (
          <EmptyState
            title={t('oncall.noPolicies')}
            description={t('oncall.noPoliciesHint')}
            icon={<CalendarClock size={22} />}
          />
        )}
        {policies && policies.length > 0 && (
          <table className="data-table data-table--dense">
            <thead>
              <tr>
                <th scope="col">{t('oncall.colKey')}</th>
                <th scope="col">{t('oncall.colName')}</th>
                <th scope="col">{t('oncall.colSteps')}</th>
                <th scope="col" aria-label={t('app.actions')} />
              </tr>
            </thead>
            <tbody>
              {policies.map((policy) => (
                <tr key={policy.id}>
                  <td>{policy.policyKey}</td>
                  <td>
                    {policy.name}
                    {!policy.active && (
                      <Badge tone="neutral" className="ml-2">
                        {t('oncall.inactive')}
                      </Badge>
                    )}
                  </td>
                  <td className="muted">
                    {policy.steps
                      .map(
                        (step) =>
                          `+${step.delayMinutes}m → ${step.targetType === 'SCHEDULE' ? '@' : ''}${step.targetRef}`,
                      )
                      .join(' · ')}
                  </td>
                  <td className="row-actions">
                    <Button variant="ghost" size="sm" onClick={() => setPolicyForm(policy)}>
                      {t('oncall.edit')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={<Trash2 size={14} />}
                      disabled={busy}
                      onClick={() =>
                        void run(
                          () => deleteEscalationPolicy(policy.policyKey),
                          'oncall.policyDeleted',
                          'oncall.saveFailed',
                        )
                      }
                    >
                      {t('oncall.delete')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {scheduleForm && (
        <ScheduleModal
          schedule={scheduleForm === 'new' ? null : scheduleForm}
          busy={busy}
          onClose={() => setScheduleForm(null)}
          onSave={async (input, existing) => {
            await run(
              () => saveOnCallSchedule(input, existing),
              'oncall.scheduleSaved',
              'oncall.saveFailed',
            );
            setScheduleForm(null);
          }}
        />
      )}

      {policyForm && (
        <PolicyModal
          policy={policyForm === 'new' ? null : policyForm}
          scheduleKeys={(schedules ?? []).map((schedule) => schedule.scheduleKey)}
          busy={busy}
          onClose={() => setPolicyForm(null)}
          onSave={async (input, existing) => {
            await run(
              () => saveEscalationPolicy(input, existing),
              'oncall.policySaved',
              'oncall.saveFailed',
            );
            setPolicyForm(null);
          }}
        />
      )}

      {overridesFor && (
        <OverridesModal
          scheduleKey={overridesFor}
          busy={busy}
          onClose={() => setOverridesFor(null)}
          onChanged={load}
        />
      )}
    </section>
  );
}

function ScheduleModal({
  schedule,
  busy,
  onClose,
  onSave,
}: {
  schedule: OnCallSchedule | null;
  busy: boolean;
  onClose: () => void;
  onSave: (
    input: {
      scheduleKey: string;
      name: string;
      timeZone: string;
      rotationHours: number;
      rotationStart: string;
      active: boolean;
      participants: string[];
    },
    existing: boolean,
  ) => void;
}) {
  const t = useT();
  const [scheduleKey, setScheduleKey] = useState(schedule?.scheduleKey ?? '');
  const [name, setName] = useState(schedule?.name ?? '');
  const [timeZone, setTimeZone] = useState(schedule?.timeZone ?? 'UTC');
  const [rotationHours, setRotationHours] = useState(String(schedule?.rotationHours ?? 168));
  const [rotationStart, setRotationStart] = useState(
    toLocalInput(schedule?.rotationStart ?? new Date().toISOString()),
  );
  const [active, setActive] = useState(schedule?.active ?? true);
  const [participants, setParticipants] = useState((schedule?.participants ?? []).join(', '));

  const parsedHours = Number.parseInt(rotationHours, 10);
  const list = participants
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
  const invalid =
    !scheduleKey.trim() ||
    !name.trim() ||
    list.length === 0 ||
    !Number.isInteger(parsedHours) ||
    parsedHours < 1 ||
    parsedHours > 8760;

  return (
    <Modal open onClose={onClose} title={schedule ? t('oncall.editSchedule') : t('oncall.newSchedule')}>
      <div className="form-grid">
        <Input
          name="oncall-key"
          label={t('oncall.colKey')}
          value={scheduleKey}
          disabled={Boolean(schedule)}
          onChange={(event) => setScheduleKey(event.target.value)}
        />
        <Input
          name="oncall-name"
          label={t('oncall.colName')}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
        <Input
          name="oncall-zone"
          label={t('oncall.timeZone')}
          value={timeZone}
          onChange={(event) => setTimeZone(event.target.value)}
        />
        <Input
          name="oncall-hours"
          type="number"
          min={1}
          max={8760}
          label={t('oncall.rotationHours')}
          value={rotationHours}
          onChange={(event) => setRotationHours(event.target.value)}
        />
        <Input
          name="oncall-start"
          type="datetime-local"
          label={t('oncall.rotationStart')}
          value={rotationStart}
          onChange={(event) => setRotationStart(event.target.value)}
        />
        <Input
          name="oncall-participants"
          label={t('oncall.participants')}
          hint={t('oncall.participantsHint')}
          value={participants}
          onChange={(event) => setParticipants(event.target.value)}
        />
        <Toggle checked={active} onChange={setActive} label={t('oncall.active')} />
      </div>
      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>
          {t('app.cancel')}
        </Button>
        <Button
          disabled={busy || invalid}
          onClick={() =>
            onSave(
              {
                scheduleKey: scheduleKey.trim(),
                name: name.trim(),
                timeZone: timeZone.trim() || 'UTC',
                rotationHours: parsedHours,
                rotationStart: new Date(rotationStart).toISOString(),
                active,
                participants: list,
              },
              Boolean(schedule),
            )
          }
        >
          {t('app.save')}
        </Button>
      </div>
    </Modal>
  );
}

function PolicyModal({
  policy,
  scheduleKeys,
  busy,
  onClose,
  onSave,
}: {
  policy: EscalationPolicy | null;
  scheduleKeys: string[];
  busy: boolean;
  onClose: () => void;
  onSave: (
    input: { policyKey: string; name: string; active: boolean; steps: EscalationStep[] },
    existing: boolean,
  ) => void;
}) {
  const t = useT();
  const [policyKey, setPolicyKey] = useState(policy?.policyKey ?? '');
  const [name, setName] = useState(policy?.name ?? '');
  const [active, setActive] = useState(policy?.active ?? true);
  const [steps, setSteps] = useState<EscalationStep[]>(
    policy?.steps ?? [{ stepOrder: 0, delayMinutes: 0, targetType: 'SCHEDULE', targetRef: '' }],
  );

  function patchStep(index: number, patch: Partial<EscalationStep>) {
    setSteps((prev) => prev.map((step, i) => (i === index ? { ...step, ...patch } : step)));
  }

  const delaysOrdered = steps.every(
    (step, index) => index === 0 || step.delayMinutes >= steps[index - 1]!.delayMinutes,
  );
  const invalid =
    !policyKey.trim() ||
    !name.trim() ||
    steps.length === 0 ||
    steps.some((step) => !step.targetRef.trim()) ||
    !delaysOrdered;

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={policy ? t('oncall.editPolicy') : t('oncall.newPolicy')}
    >
      <div className="form-grid">
        <Input
          name="policy-key"
          label={t('oncall.colKey')}
          value={policyKey}
          disabled={Boolean(policy)}
          onChange={(event) => setPolicyKey(event.target.value)}
        />
        <Input
          name="policy-name"
          label={t('oncall.colName')}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
        <Toggle checked={active} onChange={setActive} label={t('oncall.active')} />
      </div>

      <h3>{t('oncall.colSteps')}</h3>
      {!delaysOrdered && (
        <p className="field__error" role="alert">
          {t('oncall.delaysOrdered')}
        </p>
      )}
      {steps.map((step, index) => (
        <div className="oncall-step" key={index}>
          <Input
            name={`step-delay-${index}`}
            type="number"
            min={0}
            max={10080}
            label={t('oncall.delayMinutes')}
            value={String(step.delayMinutes)}
            onChange={(event) =>
              patchStep(index, { delayMinutes: Number.parseInt(event.target.value, 10) || 0 })
            }
          />
          <Select
            name={`step-type-${index}`}
            label={t('oncall.targetType')}
            value={step.targetType}
            onChange={(event) =>
              patchStep(index, {
                targetType: event.target.value as EscalationStep['targetType'],
                targetRef: '',
              })
            }
            options={[
              { value: 'SCHEDULE', label: t('oncall.targetSchedule') },
              { value: 'SUBJECT', label: t('oncall.targetSubject') },
            ]}
          />
          {step.targetType === 'SCHEDULE' ? (
            <Select
              name={`step-ref-${index}`}
              label={t('oncall.targetRef')}
              value={step.targetRef}
              placeholder={t('oncall.targetRef')}
              onChange={(event) => patchStep(index, { targetRef: event.target.value })}
              options={scheduleKeys.map((key) => ({ value: key, label: key }))}
            />
          ) : (
            <Input
              name={`step-ref-${index}`}
              label={t('oncall.targetRef')}
              value={step.targetRef}
              onChange={(event) => patchStep(index, { targetRef: event.target.value })}
            />
          )}
          <Button
            variant="ghost"
            size="sm"
            icon={<Trash2 size={14} />}
            disabled={steps.length === 1}
            onClick={() => setSteps((prev) => prev.filter((_, i) => i !== index))}
          >
            {t('oncall.delete')}
          </Button>
        </div>
      ))}
      <Button
        variant="secondary"
        icon={<Plus size={14} />}
        onClick={() =>
          setSteps((prev) => [
            ...prev,
            {
              stepOrder: prev.length,
              delayMinutes: (prev.at(-1)?.delayMinutes ?? 0) + 15,
              targetType: 'SUBJECT',
              targetRef: '',
            },
          ])
        }
      >
        {t('oncall.addStep')}
      </Button>

      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>
          {t('app.cancel')}
        </Button>
        <Button
          disabled={busy || invalid}
          onClick={() =>
            onSave(
              {
                policyKey: policyKey.trim(),
                name: name.trim(),
                active,
                steps: steps.map((step, index) => ({
                  ...step,
                  stepOrder: index,
                  targetRef: step.targetRef.trim(),
                })),
              },
              Boolean(policy),
            )
          }
        >
          {t('app.save')}
        </Button>
      </div>
    </Modal>
  );
}

function OverridesModal({
  scheduleKey,
  busy,
  onClose,
  onChanged,
}: {
  scheduleKey: string;
  busy: boolean;
  onClose: () => void;
  onChanged: () => Promise<void>;
}) {
  const t = useT();
  const { locale } = useI18n();
  const toast = useToast();
  const [overrides, setOverrides] = useState<OnCallOverride[] | null>(null);
  const [subject, setSubject] = useState('');
  const [startsAt, setStartsAt] = useState(toLocalInput(new Date().toISOString()));
  const [endsAt, setEndsAt] = useState(
    toLocalInput(new Date(Date.now() + 8 * 3_600_000).toISOString()),
  );
  const [reason, setReason] = useState('');
  const [working, setWorking] = useState(false);

  const load = useCallback(async () => {
    try {
      setOverrides(await fetchOnCallOverrides(scheduleKey));
    } catch (err) {
      toast.error(errorMessage(err, t('oncall.loadFailed')));
    }
    // toast identity is stable for the provider lifetime
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scheduleKey, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const windowInvalid = new Date(endsAt).getTime() <= new Date(startsAt).getTime();

  async function act(action: () => Promise<unknown>, successKey: string) {
    setWorking(true);
    try {
      await action();
      toast.success(t(successKey));
      await load();
      await onChanged();
    } catch (err) {
      toast.error(errorMessage(err, t('oncall.saveFailed')));
    } finally {
      setWorking(false);
    }
  }

  return (
    <Modal open onClose={onClose} size="lg" title={`${t('oncall.overrides')} · ${scheduleKey}`}>
      {!overrides && <Skeleton height={80} />}
      {overrides && overrides.length === 0 && <p className="muted">{t('oncall.noOverrides')}</p>}
      {overrides && overrides.length > 0 && (
        <table className="data-table data-table--dense">
          <thead>
            <tr>
              <th scope="col">{t('oncall.colSubject')}</th>
              <th scope="col">{t('oncall.colWindow')}</th>
              <th scope="col">{t('oncall.colReason')}</th>
              <th scope="col" aria-label={t('app.actions')} />
            </tr>
          </thead>
          <tbody>
            {overrides.map((override) => (
              <tr key={override.id}>
                <td>{override.subject}</td>
                <td className="muted">
                  {formatDateTime(override.startsAt, locale)} →{' '}
                  {formatDateTime(override.endsAt, locale)}
                </td>
                <td className="muted">{override.reason ?? '—'}</td>
                <td>
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={<Trash2 size={14} />}
                    disabled={busy || working}
                    onClick={() =>
                      void act(
                        () => deleteOnCallOverride(scheduleKey, override.id),
                        'oncall.overrideDeleted',
                      )
                    }
                  >
                    {t('oncall.delete')}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="form-grid mt-4">
        <Input
          name="override-subject"
          label={t('oncall.colSubject')}
          value={subject}
          onChange={(event) => setSubject(event.target.value)}
        />
        <Input
          name="override-start"
          type="datetime-local"
          label={t('oncall.overrideStart')}
          value={startsAt}
          onChange={(event) => setStartsAt(event.target.value)}
        />
        <Input
          name="override-end"
          type="datetime-local"
          label={t('oncall.overrideEnd')}
          value={endsAt}
          error={windowInvalid ? t('oncall.windowInvalid') : undefined}
          onChange={(event) => setEndsAt(event.target.value)}
        />
        <Input
          name="override-reason"
          label={t('oncall.colReason')}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </div>
      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>
          {t('app.close')}
        </Button>
        <Button
          disabled={busy || working || !subject.trim() || windowInvalid}
          onClick={() =>
            void act(
              () =>
                addOnCallOverride(scheduleKey, {
                  subject: subject.trim(),
                  startsAt: new Date(startsAt).toISOString(),
                  endsAt: new Date(endsAt).toISOString(),
                  reason: reason.trim() || undefined,
                }),
              'oncall.overrideAdded',
            ).then(() => {
              setSubject('');
              setReason('');
            })
          }
        >
          {t('oncall.addOverride')}
        </Button>
      </div>
    </Modal>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { Clock3, Trash2 } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useToast } from '@/hooks/useToast';
import {
  deleteWorklog,
  fetchWorklogs,
  formatMinutes,
  logTime,
} from '@/api/worklogs';
import { Button, EmptyState, Input, Skeleton, Toggle } from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import type { WorklogSummary } from '@/types';

function errorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'body' in err) {
    const body = (err as { body?: { message?: string; detail?: string } }).body;
    if (body?.detail) return body.detail;
    if (body?.message) return body.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

/** `2026-08-21T18:30:00Z` → `2026-08-21T18:30` for a datetime-local input. */
function toLocalInput(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function WorklogPanel({
  workItemId,
  canLogTime,
}: {
  workItemId: string;
  canLogTime: boolean;
}) {
  const t = useT();
  const { locale } = useI18n();
  const toast = useToast();

  const [summary, setSummary] = useState<WorklogSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [minutes, setMinutes] = useState('');
  const [startedAt, setStartedAt] = useState(() => toLocalInput(new Date()));
  const [note, setNote] = useState('');
  const [billable, setBillable] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setSummary(await fetchWorklogs(workItemId));
    } catch (err) {
      toast.error(errorMessage(err, t('worklog.loadFailed')));
    } finally {
      setLoading(false);
    }
    // toast identity is stable for the provider lifetime
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workItemId, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const parsedMinutes = Number.parseInt(minutes, 10);
  const minutesInvalid =
    minutes.trim() !== '' &&
    (!Number.isInteger(parsedMinutes) || parsedMinutes <= 0 || parsedMinutes > 1440);

  async function submit() {
    setBusy(true);
    try {
      await logTime(workItemId, {
        minutes: parsedMinutes,
        startedAt: new Date(startedAt).toISOString(),
        note: note.trim() || undefined,
        billable,
      });
      toast.success(t('worklog.logged'));
      setMinutes('');
      setNote('');
      setBillable(false);
      setStartedAt(toLocalInput(new Date()));
      await load();
    } catch (err) {
      toast.error(errorMessage(err, t('worklog.logFailed')));
    } finally {
      setBusy(false);
    }
  }

  async function remove(worklogId: string) {
    setBusy(true);
    try {
      await deleteWorklog(workItemId, worklogId);
      toast.success(t('worklog.deleted'));
      await load();
    } catch (err) {
      toast.error(errorMessage(err, t('worklog.deleteFailed')));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel detail-panel worklog-panel">
      {loading && !summary && <Skeleton height={120} />}

      {summary && (
        <>
          <div className="worklog-totals">
            <div className="sla-card">
              <span>{t('worklog.totalLogged')}</span>
              <b>
                <Clock3 size={16} aria-hidden /> {formatMinutes(summary.totalMinutes)}
              </b>
            </div>
            <div className="sla-card">
              <span>{t('worklog.billable')}</span>
              <b>{formatMinutes(summary.billableMinutes)}</b>
            </div>
            <div className="sla-card">
              <span>{t('worklog.entries')}</span>
              <b>{summary.items.length}</b>
            </div>
          </div>

          {summary.items.length === 0 ? (
            <EmptyState
              title={t('worklog.emptyTitle')}
              description={t('worklog.emptyHint')}
              icon={<Clock3 size={22} />}
            />
          ) : (
            <table className="data-table data-table--dense">
              <thead>
                <tr>
                  <th scope="col">{t('worklog.colStarted')}</th>
                  <th scope="col">{t('worklog.colAuthor')}</th>
                  <th scope="col">{t('worklog.colMinutes')}</th>
                  <th scope="col">{t('worklog.colNote')}</th>
                  <th scope="col" aria-label={t('app.actions')} />
                </tr>
              </thead>
              <tbody>
                {summary.items.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatDateTime(entry.startedAt, locale)}</td>
                    <td>{entry.authorSubject}</td>
                    <td>
                      {formatMinutes(entry.minutes)}
                      {entry.billable && (
                        <span className="chip chip--muted" style={{ marginLeft: 6 }}>
                          {t('worklog.billableShort')}
                        </span>
                      )}
                    </td>
                    <td className="muted">{entry.note ?? '—'}</td>
                    <td>
                      {canLogTime && (
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={<Trash2 size={14} />}
                          disabled={busy}
                          onClick={() => void remove(entry.id)}
                        >
                          {t('worklog.delete')}
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}

      {canLogTime && (
        <div className="worklog-form">
          <Input
            name="worklog-minutes"
            type="number"
            min={1}
            max={1440}
            label={t('worklog.colMinutes')}
            value={minutes}
            error={minutesInvalid ? t('worklog.minutesInvalid') : undefined}
            onChange={(event) => setMinutes(event.target.value)}
          />
          <Input
            name="worklog-started"
            type="datetime-local"
            label={t('worklog.colStarted')}
            value={startedAt}
            onChange={(event) => setStartedAt(event.target.value)}
          />
          <Input
            name="worklog-note"
            label={t('worklog.colNote')}
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
          <Toggle
            checked={billable}
            onChange={setBillable}
            label={t('worklog.billable')}
          />
          <Button
            disabled={busy || minutesInvalid || !minutes.trim() || !startedAt}
            onClick={() => void submit()}
          >
            {t('worklog.log')}
          </Button>
        </div>
      )}
    </section>
  );
}

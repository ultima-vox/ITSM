import { useCallback, useEffect, useState } from 'react';
import { Megaphone, Plus, Trash2 } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useToast } from '@/hooks/useToast';
import {
  createAnnouncement,
  deleteAnnouncement,
  fetchAnnouncements,
  retireAnnouncement,
  updateAnnouncement,
} from '@/api/announcements';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  Modal,
  Select,
  Skeleton,
  Textarea,
  Toggle,
} from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import type {
  Announcement,
  AnnouncementAudience,
  AnnouncementInput,
  AnnouncementSeverity,
} from '@/types';

const SEVERITIES: AnnouncementSeverity[] = ['INFO', 'WARNING', 'CRITICAL'];
const AUDIENCES: AnnouncementAudience[] = ['ALL', 'AGENTS', 'REQUESTERS'];

function severityTone(severity: AnnouncementSeverity): 'neutral' | 'amber' | 'rose' {
  if (severity === 'CRITICAL') return 'rose';
  if (severity === 'WARNING') return 'amber';
  return 'neutral';
}

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

function live(entry: Announcement, now: number): boolean {
  return (
    entry.published &&
    new Date(entry.startsAt).getTime() <= now &&
    (entry.endsAt === null || new Date(entry.endsAt).getTime() > now)
  );
}

export function AnnouncementsPage() {
  const t = useT();
  const { locale } = useI18n();
  const toast = useToast();

  const [announcements, setAnnouncements] = useState<Announcement[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<Announcement | 'new' | null>(null);

  const load = useCallback(async () => {
    try {
      setAnnouncements(await fetchAnnouncements());
    } catch (err) {
      toast.error(errorMessage(err, t('announcements.loadFailed')));
    }
    // toast identity is stable for the provider lifetime
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(action: () => Promise<unknown>, successKey: string) {
    setBusy(true);
    try {
      await action();
      toast.success(t(successKey));
      await load();
    } catch (err) {
      toast.error(errorMessage(err, t('announcements.saveFailed')));
    } finally {
      setBusy(false);
    }
  }

  const now = Date.now();

  return (
    <section className="page page--announcements">
      <div className="page-head">
        <div>
          <h1>{t('announcements.title')}</h1>
          <p className="page-subtitle">{t('announcements.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <Button icon={<Plus size={16} />} onClick={() => setEditing('new')}>
            {t('announcements.create')}
          </Button>
        </div>
      </div>

      {!announcements && <Skeleton height={140} />}

      {announcements && announcements.length === 0 && (
        <EmptyState
          title={t('announcements.emptyTitle')}
          description={t('announcements.emptyHint')}
          icon={<Megaphone size={22} />}
          actionLabel={t('announcements.create')}
          onAction={() => setEditing('new')}
        />
      )}

      {announcements && announcements.length > 0 && (
        <div className="data-table-wrap panel">
          <table className="data-table data-table--dense">
            <thead>
              <tr>
                <th scope="col">{t('announcements.colTitle')}</th>
                <th scope="col">{t('announcements.colSeverity')}</th>
                <th scope="col">{t('announcements.colAudience')}</th>
                <th scope="col">{t('announcements.colWindow')}</th>
                <th scope="col">{t('announcements.colState')}</th>
                <th scope="col" aria-label={t('app.actions')} />
              </tr>
            </thead>
            <tbody>
              {announcements.map((entry) => (
                <tr key={entry.id}>
                  <td>{entry.title}</td>
                  <td>
                    <Badge tone={severityTone(entry.severity)}>
                      {t(`announcements.severity.${entry.severity}`)}
                    </Badge>
                  </td>
                  <td className="muted">{t(`announcements.audience.${entry.audience}`)}</td>
                  <td className="muted">
                    {formatDateTime(entry.startsAt, locale)}
                    {' → '}
                    {entry.endsAt ? formatDateTime(entry.endsAt, locale) : t('announcements.openEnded')}
                  </td>
                  <td>
                    {live(entry, now) ? (
                      <Badge tone="mint">{t('announcements.stateLive')}</Badge>
                    ) : entry.published ? (
                      <span className="muted">{t('announcements.stateScheduled')}</span>
                    ) : (
                      <span className="muted">{t('announcements.stateDraft')}</span>
                    )}
                  </td>
                  <td className="row-actions">
                    <Button variant="ghost" size="sm" onClick={() => setEditing(entry)}>
                      {t('announcements.edit')}
                    </Button>
                    {live(entry, now) && (
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={busy}
                        onClick={() =>
                          void run(() => retireAnnouncement(entry.id), 'announcements.retired')
                        }
                      >
                        {t('announcements.retire')}
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={<Trash2 size={14} />}
                      disabled={busy}
                      onClick={() =>
                        void run(() => deleteAnnouncement(entry.id), 'announcements.deleted')
                      }
                    >
                      {t('announcements.delete')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <AnnouncementModal
          announcement={editing === 'new' ? null : editing}
          busy={busy}
          onClose={() => setEditing(null)}
          onSave={async (input, id) => {
            await run(
              () => (id ? updateAnnouncement(id, input) : createAnnouncement(input)),
              id ? 'announcements.saved' : 'announcements.created',
            );
            setEditing(null);
          }}
        />
      )}
    </section>
  );
}

function AnnouncementModal({
  announcement,
  busy,
  onClose,
  onSave,
}: {
  announcement: Announcement | null;
  busy: boolean;
  onClose: () => void;
  onSave: (input: AnnouncementInput, id?: string) => void;
}) {
  const t = useT();
  const [title, setTitle] = useState(announcement?.title ?? '');
  const [body, setBody] = useState(announcement?.body ?? '');
  const [severity, setSeverity] = useState<AnnouncementSeverity>(announcement?.severity ?? 'INFO');
  const [audience, setAudience] = useState<AnnouncementAudience>(announcement?.audience ?? 'ALL');
  const [startsAt, setStartsAt] = useState(
    toLocalInput(announcement?.startsAt ?? new Date().toISOString()),
  );
  const [endsAt, setEndsAt] = useState(announcement?.endsAt ? toLocalInput(announcement.endsAt) : '');
  const [published, setPublished] = useState(announcement?.published ?? false);
  const [dismissible, setDismissible] = useState(announcement?.dismissible ?? true);
  const [linkUrl, setLinkUrl] = useState(announcement?.linkUrl ?? '');

  const windowInvalid =
    Boolean(endsAt) && new Date(endsAt).getTime() <= new Date(startsAt).getTime();
  const invalid = !title.trim() || !body.trim() || !startsAt || windowInvalid;

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={announcement ? t('announcements.edit') : t('announcements.create')}
    >
      <div className="form-grid">
        <Input
          name="announcement-title"
          label={t('announcements.colTitle')}
          value={title}
          required
          onChange={(event) => setTitle(event.target.value)}
        />
        <Select
          name="announcement-severity"
          label={t('announcements.colSeverity')}
          value={severity}
          onChange={(event) => setSeverity(event.target.value as AnnouncementSeverity)}
          options={SEVERITIES.map((value) => ({
            value,
            label: t(`announcements.severity.${value}`),
          }))}
        />
        <Select
          name="announcement-audience"
          label={t('announcements.colAudience')}
          value={audience}
          onChange={(event) => setAudience(event.target.value as AnnouncementAudience)}
          options={AUDIENCES.map((value) => ({
            value,
            label: t(`announcements.audience.${value}`),
          }))}
        />
        <Input
          name="announcement-start"
          type="datetime-local"
          label={t('announcements.startsAt')}
          value={startsAt}
          onChange={(event) => setStartsAt(event.target.value)}
        />
        <Input
          name="announcement-end"
          type="datetime-local"
          label={t('announcements.endsAt')}
          hint={t('announcements.endsAtHint')}
          value={endsAt}
          error={windowInvalid ? t('announcements.windowInvalid') : undefined}
          onChange={(event) => setEndsAt(event.target.value)}
        />
        <Input
          name="announcement-link"
          label={t('announcements.linkUrl')}
          value={linkUrl}
          onChange={(event) => setLinkUrl(event.target.value)}
        />
      </div>
      <Textarea
        name="announcement-body"
        label={t('announcements.body')}
        value={body}
        rows={4}
        onChange={(event) => setBody(event.target.value)}
      />
      <div className="release-detail__row">
        <Toggle checked={published} onChange={setPublished} label={t('announcements.published')} />
        <Toggle
          checked={dismissible}
          onChange={setDismissible}
          label={t('announcements.dismissible')}
        />
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
                title: title.trim(),
                body: body.trim(),
                severity,
                audience,
                startsAt: new Date(startsAt).toISOString(),
                endsAt: endsAt ? new Date(endsAt).toISOString() : null,
                published,
                dismissible,
                linkUrl: linkUrl.trim() || null,
                expectedVersion: announcement?.version,
              },
              announcement?.id,
            )
          }
        >
          {t('app.save')}
        </Button>
      </div>
    </Modal>
  );
}

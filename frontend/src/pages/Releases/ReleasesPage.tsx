import { useCallback, useEffect, useMemo, useState } from 'react';
import { Rocket, Filter, Plus, Link2, Trash2, ShieldCheck, ShieldX } from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useToast } from '@/hooks/useToast';
import {
  createRelease,
  fetchRelease,
  fetchReleaseContent,
  fetchReleaseTransitions,
  fetchReleases,
  linkReleaseChanges,
  recordGoDecision,
  transitionRelease,
  unlinkReleaseChange,
  updateRelease,
} from '@/api/releases';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Select,
  Skeleton,
  Textarea,
} from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import type {
  Release,
  ReleaseContent,
  ReleaseStatus,
  ReleaseType,
} from '@/types';

const STATUSES: ReleaseStatus[] = [
  'PLANNING',
  'BUILD',
  'TESTING',
  'GO_NO_GO',
  'DEPLOYING',
  'DEPLOYED',
  'ROLLED_BACK',
  'CLOSED',
  'CANCELLED',
];

const TYPES: ReleaseType[] = ['MAJOR', 'MINOR', 'PATCH', 'EMERGENCY'];

function statusTone(status: ReleaseStatus): 'neutral' | 'violet' | 'mint' | 'amber' | 'rose' | 'blue' {
  switch (status) {
    case 'PLANNING':
      return 'neutral';
    case 'BUILD':
    case 'TESTING':
      return 'violet';
    case 'GO_NO_GO':
      return 'amber';
    case 'DEPLOYING':
      return 'blue';
    case 'DEPLOYED':
      return 'mint';
    case 'ROLLED_BACK':
    case 'CANCELLED':
      return 'rose';
    default:
      return 'neutral';
  }
}

function errorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'body' in err) {
    const body = (err as { body?: { message?: string } }).body;
    if (body?.message) return body.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

export function ReleasesPage() {
  const t = useT();
  const { locale } = useI18n();
  const toast = useToast();

  const [status, setStatus] = useState<ReleaseStatus | 'all'>('all');
  const [type, setType] = useState<ReleaseType | 'all'>('all');
  const [q, setQ] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);

  const { data, loading, error, reload } = useAsync(
    () => fetchReleases({ status, type, q, size: 200 }),
    [status, type, q, refreshTick],
  );

  const releases = useMemo(() => data?.items ?? [], [data]);

  const [selected, setSelected] = useState<Release | null>(null);
  const [content, setContent] = useState<ReleaseContent | null>(null);
  const [targets, setTargets] = useState<ReleaseStatus[]>([]);
  const [detailError, setDetailError] = useState<string | null>(null);

  const loadDetail = useCallback(
    async (id: string) => {
      setDetailError(null);
      try {
        const release = await fetchRelease(id);
        setSelected(release);
        const [releaseContent, available] = await Promise.all([
          fetchReleaseContent(id),
          fetchReleaseTransitions(id, release.status),
        ]);
        setContent(releaseContent);
        setTargets(available);
      } catch (err) {
        setDetailError(errorMessage(err, t('releases.detailError')));
      }
    },
    [t],
  );

  useEffect(() => {
    if (!selectedId) {
      setSelected(null);
      setContent(null);
      setTargets([]);
      return;
    }
    void loadDetail(selectedId);
  }, [selectedId, loadDetail]);

  const refresh = useCallback(
    async (id?: string) => {
      setRefreshTick((n) => n + 1);
      if (id) await loadDetail(id);
    },
    [loadDetail],
  );

  async function runAction(action: () => Promise<unknown>, successKey: string, fallbackKey: string) {
    setBusy(true);
    try {
      await action();
      toast.success(t(successKey));
      await refresh(selectedId ?? undefined);
    } catch (err) {
      toast.error(errorMessage(err, t(fallbackKey)));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="page page--releases">
      <div className="page-head">
        <div>
          <h1>{t('releases.title')}</h1>
          <p className="page-subtitle">{t('releases.subtitle')}</p>
        </div>
        <div className="page-head__meta">
          <span className="chip">
            <Rocket size={14} aria-hidden />
            {t('releases.count', { n: data?.total ?? releases.length })}
          </span>
          <Button icon={<Plus size={16} />} onClick={() => setCreateOpen(true)}>
            {t('releases.create')}
          </Button>
        </div>
      </div>

      <div className="filter-bar">
        <Input
          name="release-search"
          value={q}
          placeholder={t('releases.searchPlaceholder')}
          onChange={(event) => setQ(event.target.value)}
          aria-label={t('releases.searchPlaceholder')}
        />
        <Select
          name="release-status"
          value={status}
          onChange={(event) => setStatus(event.target.value as ReleaseStatus | 'all')}
          aria-label={t('releases.filterStatus')}
          options={[
            { value: 'all', label: t('app.all') },
            ...STATUSES.map((value) => ({ value, label: t(`releases.status.${value}`) })),
          ]}
        />
        <Select
          name="release-type"
          value={type}
          onChange={(event) => setType(event.target.value as ReleaseType | 'all')}
          aria-label={t('releases.filterType')}
          options={[
            { value: 'all', label: t('app.all') },
            ...TYPES.map((value) => ({ value, label: t(`releases.type.${value}`) })),
          ]}
        />
        {(status !== 'all' || type !== 'all' || q) && (
          <Button
            variant="ghost"
            icon={<Filter size={14} />}
            onClick={() => {
              setStatus('all');
              setType('all');
              setQ('');
            }}
          >
            {t('app.reset')}
          </Button>
        )}
      </div>

      {error && <ErrorState onRetry={reload} />}

      {loading && !data && (
        <div className="panel" aria-busy="true">
          <Skeleton height={36} />
          <Skeleton height={36} className="mt-2" />
          <Skeleton height={36} className="mt-2" />
        </div>
      )}

      {!loading && !error && releases.length === 0 && (
        <EmptyState
          title={t('releases.emptyTitle')}
          description={t('releases.emptyHint')}
          icon={<Rocket size={22} />}
          actionLabel={t('releases.create')}
          onAction={() => setCreateOpen(true)}
        />
      )}

      {releases.length > 0 && (
        <div className="data-table-wrap panel">
          <table className="data-table data-table--dense">
            <thead>
              <tr>
                <th scope="col">{t('releases.colNumber')}</th>
                <th scope="col">{t('releases.colName')}</th>
                <th scope="col">{t('releases.colType')}</th>
                <th scope="col">{t('releases.colStatus')}</th>
                <th scope="col">{t('releases.colWindow')}</th>
                <th scope="col">{t('releases.colManager')}</th>
              </tr>
            </thead>
            <tbody>
              {releases.map((release) => (
                <tr
                  key={release.id}
                  className={selectedId === release.id ? 'is-selected' : undefined}
                  onClick={() => setSelectedId(release.id)}
                  tabIndex={0}
                  role="button"
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      setSelectedId(release.id);
                    }
                  }}
                >
                  <td>{release.number}</td>
                  <td>{release.name}</td>
                  <td>
                    <span className="type-pill type-pill--sm">
                      {t(`releases.type.${release.type}`)}
                    </span>
                  </td>
                  <td>
                    <Badge tone={statusTone(release.status)}>
                      {t(`releases.status.${release.status}`)}
                    </Badge>
                  </td>
                  <td className="muted">
                    {release.plannedStart
                      ? formatDateTime(release.plannedStart, locale)
                      : '—'}
                  </td>
                  <td className="muted">{release.releaseManager ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={Boolean(selectedId)}
        onClose={() => setSelectedId(null)}
        title={selected ? `${selected.number} · ${selected.name}` : t('releases.title')}
        size="lg"
      >
        {detailError && <p className="field__error" role="alert">{detailError}</p>}
        {!selected && !detailError && <Skeleton height={140} />}
        {selected && (
          <div className="release-detail">
            <div className="release-detail__row">
              <Badge tone={statusTone(selected.status)}>
                {t(`releases.status.${selected.status}`)}
              </Badge>
              <span className="type-pill type-pill--sm">
                {t(`releases.type.${selected.type}`)}
              </span>
              {selected.goDecision && (
                <Badge tone={selected.goDecision === 'GO' ? 'mint' : 'rose'}>
                  {t(`releases.goDecision.${selected.goDecision}`)}
                </Badge>
              )}
            </div>

            <dl className="detail-grid">
              <div>
                <dt>{t('releases.colWindow')}</dt>
                <dd>
                  {selected.plannedStart ? formatDateTime(selected.plannedStart, locale) : '—'}
                  {' → '}
                  {selected.plannedEnd ? formatDateTime(selected.plannedEnd, locale) : '—'}
                </dd>
              </div>
              <div>
                <dt>{t('releases.actualWindow')}</dt>
                <dd>
                  {selected.actualStart ? formatDateTime(selected.actualStart, locale) : '—'}
                  {' → '}
                  {selected.actualEnd ? formatDateTime(selected.actualEnd, locale) : '—'}
                </dd>
              </div>
              <div>
                <dt>{t('releases.colManager')}</dt>
                <dd>{selected.releaseManager ?? '—'}</dd>
              </div>
              <div>
                <dt>{t('releases.deploymentPlan')}</dt>
                <dd>{selected.deploymentPlan ?? '—'}</dd>
              </div>
              <div>
                <dt>{t('releases.rollbackPlan')}</dt>
                <dd>{selected.rollbackPlan ?? '—'}</dd>
              </div>
              <div>
                <dt>{t('releases.testSummary')}</dt>
                <dd>{selected.testSummary ?? '—'}</dd>
              </div>
            </dl>

            <ReleasePlanEditor
              release={selected}
              busy={busy}
              onSave={(payload) =>
                runAction(
                  () => updateRelease(selected.id, { expectedVersion: selected.version, ...payload }),
                  'releases.saved',
                  'releases.saveFailed',
                )
              }
            />

            <section className="release-detail__section">
              <h3>{t('releases.contentTitle')}</h3>
              {content && content.total === 0 && (
                <p className="muted">{t('releases.contentEmpty')}</p>
              )}
              {content && content.total > 0 && (
                <>
                  <p className={content.deployable ? 'muted' : 'field__error'}>
                    {content.deployable
                      ? t('releases.contentReady')
                      : t('releases.contentBlocking', { n: content.blocking })}
                  </p>
                  <table className="data-table data-table--dense">
                    <thead>
                      <tr>
                        <th scope="col">{t('releases.colNumber')}</th>
                        <th scope="col">{t('releases.colName')}</th>
                        <th scope="col">{t('releases.colStatus')}</th>
                        <th scope="col" aria-label={t('app.actions')} />
                      </tr>
                    </thead>
                    <tbody>
                      {content.items.map((entry) => (
                        <tr key={entry.changeId}>
                          <td>{entry.number}</td>
                          <td>{entry.title}</td>
                          <td>
                            <Badge tone={entry.deployable ? 'mint' : 'amber'}>{entry.status}</Badge>
                          </td>
                          <td>
                            <Button
                              variant="ghost"
                              size="sm"
                              icon={<Trash2 size={14} />}
                              disabled={busy}
                              onClick={() =>
                                runAction(
                                  () => unlinkReleaseChange(selected.id, entry.changeId),
                                  'releases.unlinked',
                                  'releases.unlinkFailed',
                                )
                              }
                            >
                              {t('releases.unlink')}
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )}
              <LinkChangeForm
                busy={busy}
                onLink={(changeId) =>
                  runAction(
                    () => linkReleaseChanges(selected.id, [changeId]),
                    'releases.linked',
                    'releases.linkFailed',
                  )
                }
              />
            </section>

            {selected.status === 'GO_NO_GO' && (
              <section className="release-detail__section">
                <h3>{t('releases.goNoGoTitle')}</h3>
                <div className="release-detail__row">
                  <Button
                    icon={<ShieldCheck size={16} />}
                    disabled={busy}
                    onClick={() =>
                      runAction(
                        () => recordGoDecision(selected.id, 'GO', undefined, selected.version),
                        'releases.goRecorded',
                        'releases.goFailed',
                      )
                    }
                  >
                    {t('releases.goDecision.GO')}
                  </Button>
                  <Button
                    variant="danger"
                    icon={<ShieldX size={16} />}
                    disabled={busy}
                    onClick={() =>
                      runAction(
                        () => recordGoDecision(selected.id, 'NO_GO', undefined, selected.version),
                        'releases.goRecorded',
                        'releases.goFailed',
                      )
                    }
                  >
                    {t('releases.goDecision.NO_GO')}
                  </Button>
                </div>
              </section>
            )}

            <section className="release-detail__section">
              <h3>{t('releases.transitionsTitle')}</h3>
              {targets.length === 0 && <p className="muted">{t('releases.noTransitions')}</p>}
              <div className="release-detail__row">
                {targets.map((target) => (
                  <Button
                    key={target}
                    variant="secondary"
                    disabled={busy}
                    onClick={() =>
                      runAction(
                        () => transitionRelease(selected.id, target, selected.version),
                        'releases.transitioned',
                        'releases.transitionFailed',
                      )
                    }
                  >
                    {t(`releases.status.${target}`)}
                  </Button>
                ))}
              </div>
            </section>
          </div>
        )}
      </Modal>

      <CreateReleaseModal
        open={createOpen}
        busy={busy}
        onClose={() => setCreateOpen(false)}
        onCreate={async (payload) => {
          setBusy(true);
          try {
            const created = await createRelease(payload);
            toast.success(t('releases.created'));
            setCreateOpen(false);
            setRefreshTick((n) => n + 1);
            setSelectedId(created.id);
          } catch (err) {
            toast.error(errorMessage(err, t('releases.createFailed')));
          } finally {
            setBusy(false);
          }
        }}
      />
    </section>
  );
}

interface PlanPayload {
  deploymentPlan?: string;
  rollbackPlan?: string;
  testSummary?: string;
}

function ReleasePlanEditor({
  release,
  busy,
  onSave,
}: {
  release: Release;
  busy: boolean;
  onSave: (payload: PlanPayload) => void;
}) {
  const t = useT();
  const [deploymentPlan, setDeploymentPlan] = useState(release.deploymentPlan ?? '');
  const [rollbackPlan, setRollbackPlan] = useState(release.rollbackPlan ?? '');
  const [testSummary, setTestSummary] = useState(release.testSummary ?? '');

  useEffect(() => {
    setDeploymentPlan(release.deploymentPlan ?? '');
    setRollbackPlan(release.rollbackPlan ?? '');
    setTestSummary(release.testSummary ?? '');
  }, [release.id, release.version, release.deploymentPlan, release.rollbackPlan, release.testSummary]);

  const frozen = ['DEPLOYING', 'DEPLOYED', 'ROLLED_BACK', 'CLOSED', 'CANCELLED'].includes(
    release.status,
  );
  if (frozen) return null;

  return (
    <section className="release-detail__section">
      <h3>{t('releases.plansTitle')}</h3>
      <Textarea
        name="deploymentPlan"
        label={t('releases.deploymentPlan')}
        value={deploymentPlan}
        rows={3}
        onChange={(event) => setDeploymentPlan(event.target.value)}
      />
      <Textarea
        name="rollbackPlan"
        label={t('releases.rollbackPlan')}
        value={rollbackPlan}
        rows={3}
        onChange={(event) => setRollbackPlan(event.target.value)}
      />
      <Textarea
        name="testSummary"
        label={t('releases.testSummary')}
        value={testSummary}
        rows={3}
        onChange={(event) => setTestSummary(event.target.value)}
      />
      <Button
        disabled={busy}
        onClick={() =>
          onSave({
            deploymentPlan: deploymentPlan.trim() || undefined,
            rollbackPlan: rollbackPlan.trim() || undefined,
            testSummary: testSummary.trim() || undefined,
          })
        }
      >
        {t('app.save')}
      </Button>
    </section>
  );
}

function LinkChangeForm({
  busy,
  onLink,
}: {
  busy: boolean;
  onLink: (changeId: string) => void;
}) {
  const t = useT();
  const [changeId, setChangeId] = useState('');

  return (
    <div className="release-detail__row">
      <Input
        name="release-link-change"
        value={changeId}
        placeholder={t('releases.linkPlaceholder')}
        aria-label={t('releases.linkPlaceholder')}
        onChange={(event) => setChangeId(event.target.value)}
      />
      <Button
        variant="secondary"
        icon={<Link2 size={16} />}
        disabled={busy || !changeId.trim()}
        onClick={() => {
          onLink(changeId.trim());
          setChangeId('');
        }}
      >
        {t('releases.link')}
      </Button>
    </div>
  );
}

function CreateReleaseModal({
  open,
  busy,
  onClose,
  onCreate,
}: {
  open: boolean;
  busy: boolean;
  onClose: () => void;
  onCreate: (payload: {
    name: string;
    type: ReleaseType;
    description?: string;
    plannedStart?: string;
    plannedEnd?: string;
  }) => void;
}) {
  const t = useT();
  const [name, setName] = useState('');
  const [type, setType] = useState<ReleaseType>('MINOR');
  const [description, setDescription] = useState('');
  const [plannedStart, setPlannedStart] = useState('');
  const [plannedEnd, setPlannedEnd] = useState('');

  useEffect(() => {
    if (!open) {
      setName('');
      setType('MINOR');
      setDescription('');
      setPlannedStart('');
      setPlannedEnd('');
    }
  }, [open]);

  const windowInvalid =
    Boolean(plannedStart) && Boolean(plannedEnd) && new Date(plannedEnd) <= new Date(plannedStart);

  return (
    <Modal open={open} onClose={onClose} title={t('releases.create')}>
      <div className="form-grid">
        <Input
          name="release-name"
          label={t('releases.colName')}
          value={name}
          required
          onChange={(event) => setName(event.target.value)}
        />
        <Select
          name="release-create-type"
          label={t('releases.colType')}
          value={type}
          onChange={(event) => setType(event.target.value as ReleaseType)}
          options={TYPES.map((value) => ({ value, label: t(`releases.type.${value}`) }))}
        />
        <Textarea
          name="release-description"
          label={t('releases.description')}
          value={description}
          rows={3}
          onChange={(event) => setDescription(event.target.value)}
        />
        <Input
          name="release-start"
          type="datetime-local"
          label={t('releases.plannedStart')}
          value={plannedStart}
          onChange={(event) => setPlannedStart(event.target.value)}
        />
        <Input
          name="release-end"
          type="datetime-local"
          label={t('releases.plannedEnd')}
          value={plannedEnd}
          error={windowInvalid ? t('releases.windowInvalid') : undefined}
          onChange={(event) => setPlannedEnd(event.target.value)}
        />
      </div>
      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>
          {t('app.cancel')}
        </Button>
        <Button
          disabled={busy || !name.trim() || windowInvalid}
          onClick={() =>
            onCreate({
              name: name.trim(),
              type,
              description: description.trim() || undefined,
              plannedStart: plannedStart ? new Date(plannedStart).toISOString() : undefined,
              plannedEnd: plannedEnd ? new Date(plannedEnd).toISOString() : undefined,
            })
          }
        >
          {t('releases.create')}
        </Button>
      </div>
    </Modal>
  );
}

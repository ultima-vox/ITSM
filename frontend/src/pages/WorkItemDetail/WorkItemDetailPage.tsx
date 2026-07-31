import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  CheckCircle2,
  Clock3,
  Eye,
  EyeOff,
  MessageSquare,
  Paperclip,
  Plus,
  ShieldAlert,
  UserPlus,
  Activity,
  Boxes,
  Link2,
  AlertTriangle,
  ListTodo,
  MessageCircleQuestion,
  X,
  GitBranch,
} from 'lucide-react';
import { useT, useI18n } from '@/i18n';
import { useAsync } from '@/hooks/useAsync';
import { useWorkItemsSync } from '@/hooks/useWorkItemsSync';
import { useToast } from '@/hooks/useToast';
import {
  fetchWorkItem,
  fetchWorkItemActivity,
  fetchWorkItemComments,
  fetchConfigurationItems,
  fetchWorkItems,
  fetchKnowledgeArticles,
  fetchProblems,
  fetchFormDefinition,
  assignWorkItemToMe,
  escalateWorkItem,
  resolveWorkItem,
  patchWorkItem,
  addWorkItemComment,
  watchWorkItem,
  unwatchWorkItem,
  listWorkItemAttachments,
  uploadAndLinkWorkItemAttachment,
  unlinkWorkItemAttachment,
  getContentUrl,
  formatBytes,
  type AttachmentMeta,
  type FormDefinition,
} from '@/api';
import {
  Avatar,
  Button,
  EmptyState,
  ErrorState,
  Modal,
  Select,
  Skeleton,
  Tabs,
  Textarea,
} from '@/components/ui';
import { DynamicForm } from '@/components/form/DynamicForm';
import { PriorityBadge, StatusChip } from '@/components/data-display';
import { formatDateTime, formatRelative } from '@/lib/format';
import { slaConsumedPct } from '@/lib/sla';
import {
  findResolveTransition,
  getWorkItemRuntimeTransitions,
  workflowStateLabelKey,
  WORK_ITEM_ACTION_PERMISSIONS,
  type WorkItemRuntimeTransition,
} from '@/lib/workflowRuntime';
import {
  getActiveWorkflowDefinition,
  subscribeWorkflowDefinitions,
} from '@/mock/workflow';
import {
  getUserPermissions,
  missingPermissionsFor,
  subscribeRbac,
} from '@/mock/rbac';
import { currentUser } from '@/mock/data';
import type {
  ImpactLevel,
  SlaState,
  UrgencyLevel,
  WorkItemActivity,
  WorkItemStatus,
} from '@/types';

function policyKey(priority: string): string {
  if (priority === 'critical') return 'sla.policyP1';
  if (priority === 'high') return 'sla.policyP2';
  return 'sla.policyP3';
}

function responseMins(priority: string): number {
  if (priority === 'critical') return 15;
  if (priority === 'high') return 30;
  return 60;
}

function resolutionHours(priority: string): number {
  if (priority === 'critical') return 4;
  if (priority === 'high') return 8;
  return 24;
}

function activityIcon(kind: WorkItemActivity['kind']) {
  switch (kind) {
    case 'status':
      return Activity;
    case 'assignment':
      return UserPlus;
    case 'sla':
      return ShieldAlert;
    case 'comment':
      return MessageSquare;
    case 'field':
      return ListTodo;
    default:
      return CheckCircle2;
  }
}

function activityText(t: (k: string) => string, text: string) {
  const key = `workItem.activity.${text}`;
  const translated = t(key);
  return translated === key ? text : translated;
}

function formatDiffValue(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function ActivityDiff({
  before,
  after,
  fieldLabel,
}: {
  before?: Record<string, unknown> | null;
  after?: Record<string, unknown> | null;
  fieldLabel: (field: string) => string;
}) {
  if (!before && !after) return null;
  const keys = [
    ...new Set([
      ...Object.keys(before ?? {}),
      ...Object.keys(after ?? {}),
    ]),
  ];
  if (!keys.length) return null;
  return (
    <dl className="activity-diff">
      {keys.map((field) => (
        <div key={field} className="activity-diff__row">
          <dt>{fieldLabel(field)}</dt>
          <dd>
            <span className="activity-diff__before">
              {formatDiffValue(before?.[field])}
            </span>
            <span className="activity-diff__arrow" aria-hidden>
              →
            </span>
            <span className="activity-diff__after">
              {formatDiffValue(after?.[field])}
            </span>
          </dd>
        </div>
      ))}
    </dl>
  );
}

type TranslateFn = (
  key: string,
  vars?: Record<string, string | number>,
) => string;

function activityFieldLabel(t: TranslateFn, field: string): string {
  const key = `workItem.fields.${field}`;
  const translated = t(key);
  return translated === key ? field : translated;
}

/** Human label for a workflow required-field key (assignee_id → Assignee). */
function requiredFieldLabel(t: TranslateFn, field: string): string {
  const normalized = field.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
  const aliases: Record<string, string> = {
    assignee_id: 'assignee',
    resolution_notes: 'resolutionNotes',
    resolutionnotes: 'resolutionNotes',
  };
  const logical = aliases[normalized] ?? normalized;
  const fieldKey = `workItem.fields.${logical}`;
  const fromFields = t(fieldKey);
  if (fromFields !== fieldKey) return fromFields;
  const topKey = `workItem.${logical}`;
  const fromTop = t(topKey);
  if (fromTop !== topKey) return fromTop;
  return field;
}

function transitionActionLabel(
  t: TranslateFn,
  tr: WorkItemRuntimeTransition,
): string {
  const byKey = `workItem.transition.${tr.key}`;
  const translated = t(byKey);
  if (translated !== byKey) return translated;
  if (tr.toStatus) {
    const byStatus = `workItem.actions.to_${tr.toStatus}`;
    const s = t(byStatus);
    if (s !== byStatus) return s;
    return t(`status.${tr.toStatus}`);
  }
  return tr.to;
}

function transitionDisabledReason(
  t: TranslateFn,
  tr: WorkItemRuntimeTransition,
): string | undefined {
  if (tr.enabled) return undefined;
  if (tr.unsupportedTarget) {
    return t('workItem.workflowUnsupportedState', { state: tr.to });
  }
  if (tr.missingPermissions.length > 0) {
    return t('workItem.workflowMissingPermissions', {
      permissions: tr.missingPermissions.join(', '),
    });
  }
  // Match enablement: resolution_notes alone does not block resolve (modal supplies it)
  const blocking =
    tr.toStatus === 'resolved'
      ? tr.missingFields.filter((f) => {
          const k = f.trim().toLowerCase();
          return k !== 'resolution_notes' && k !== 'resolutionnotes';
        })
      : tr.missingFields;
  if (blocking.length > 0) {
    const labels = blocking.map((f) => requiredFieldLabel(t, f));
    return t('workItem.workflowMissingFields', { fields: labels.join(', ') });
  }
  if (tr.policyBlockKey) {
    const labeled = t(tr.policyBlockKey);
    return labeled === tr.policyBlockKey
      ? t('workItem.workflowTransitionBlocked')
      : labeled;
  }
  return t('workItem.workflowTransitionBlocked');
}

/** Sticky / non-workflow action disabled because principal lacks grants. */
function actionMissingPermissionReason(
  t: TranslateFn,
  missing: string[],
): string | undefined {
  if (!missing.length) return undefined;
  return t('workItem.workflowMissingPermissions', {
    permissions: missing.join(', '),
  });
}

function transitionVariant(
  tr: WorkItemRuntimeTransition,
): 'primary' | 'secondary' | 'danger' | 'ghost' {
  if (tr.toStatus === 'resolved' || tr.toStatus === 'closed') return 'primary';
  if (tr.toStatus === 'cancelled') return 'danger';
  if (tr.toStatus === 'waiting') return 'secondary';
  return 'secondary';
}

const MORE_INFO_TEMPLATE =
  'Need more information to continue. Please provide: reproduction steps, screenshots, and business impact.';

export function WorkItemDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const t = useT();
  const { locale } = useI18n();
  const [tab, setTab] = useState('details');
  const [comment, setComment] = useState('');
  const [internal, setInternal] = useState(true);
  const [resolveOpen, setResolveOpen] = useState(false);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [resolveError, setResolveError] = useState('');
  const [attachments, setAttachments] = useState<AttachmentMeta[]>([]);
  const [uploading, setUploading] = useState(false);
  const attachInputRef = useRef<HTMLInputElement>(null);
  const { success } = useToast();
  /** Bumps when admin toggles active workflow version (session store). */
  const [workflowTick, setWorkflowTick] = useState(0);
  /** Bumps when RBAC role assignment changes (session store). */
  const [rbacTick, setRbacTick] = useState(0);

  const item = useAsync(() => fetchWorkItem(id), [id]);
  const activity = useAsync(() => fetchWorkItemActivity(id), [id]);
  const commentsQ = useAsync(() => fetchWorkItemComments(id), [id]);
  const cis = useAsync(() => fetchConfigurationItems(), []);
  const allItems = useAsync(() => fetchWorkItems(), []);
  const kb = useAsync(() => fetchKnowledgeArticles(), []);
  const problems = useAsync(() => fetchProblems(), []);
  useWorkItemsSync(
    item.reload,
    activity.reload,
    commentsQ.reload,
    allItems.reload,
  );

  useEffect(() => {
    return subscribeWorkflowDefinitions(() => {
      setWorkflowTick((n) => n + 1);
    });
  }, []);

  useEffect(() => {
    return subscribeRbac(() => {
      setRbacTick((n) => n + 1);
    });
  }, []);

  // Sync local field drafts when item loads / reloads from store
  const wi = item.data;
  const [impact, setImpact] = useState<ImpactLevel>('medium');
  const [urgency, setUrgency] = useState<UrgencyLevel>('medium');
  const [formDef, setFormDef] = useState<FormDefinition | null>(null);
  const [formValues, setFormValues] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    void fetchFormDefinition('work-item').then((def) => {
      if (!cancelled) setFormDef(def);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!wi) return;
    setImpact(wi.impact ?? 'medium');
    setUrgency(wi.urgency ?? 'medium');
    setFormValues({
      title: wi.title,
      description: wi.description,
      service: wi.service,
      impact: wi.impact ?? 'medium',
      urgency: wi.urgency ?? 'medium',
    });
  }, [wi?.id, wi?.title, wi?.description, wi?.service, wi?.impact, wi?.urgency, wi?.updatedAt]);

  // Load persisted links when work item changes
  useEffect(() => {
    let cancelled = false;
    setUploading(false);
    listWorkItemAttachments(id)
      .then((list) => {
        if (!cancelled) setAttachments(list);
      })
      .catch(() => {
        if (!cancelled) setAttachments([]);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  const flash = (msg: string) => success(msg);

  const handleAttach = async (list: FileList | null) => {
    if (!list?.length) return;
    setUploading(true);
    try {
      const uploaded: AttachmentMeta[] = [];
      for (const file of Array.from(list)) {
        try {
          const meta = await uploadAndLinkWorkItemAttachment(id, file);
          uploaded.push(meta);
        } catch {
          /* continue remaining files */
        }
      }
      if (uploaded.length) {
        setAttachments((prev) => {
          const seen = new Set(prev.map((p) => p.id));
          return [...uploaded.filter((u) => !seen.has(u.id)), ...prev];
        });
        flash(t('workItem.attachmentUploaded', { n: uploaded.length }));
      }
    } finally {
      setUploading(false);
      if (attachInputRef.current) attachInputRef.current.value = '';
    }
  };

  const handleUnlink = async (attachmentId: string) => {
    try {
      await unlinkWorkItemAttachment(id, attachmentId);
      setAttachments((prev) => prev.filter((a) => a.id !== attachmentId));
      flash(t('workItem.attachmentRemoved'));
    } catch {
      /* toast optional */
    }
  };

  const handleAssign = async () => {
    await assignWorkItemToMe(id);
    flash(t('workItem.assignedToast'));
  };

  const handleEscalate = async () => {
    await escalateWorkItem(id);
    flash(t('workItem.escalatedToast'));
  };

  const openResolve = () => {
    setResolutionNotes(wi?.resolutionNotes ?? '');
    setResolveError('');
    setResolveOpen(true);
  };

  const handleWorkflowTransition = async (tr: WorkItemRuntimeTransition) => {
    if (!tr.enabled || !tr.toStatus) return;
    if (tr.toStatus === 'resolved') {
      openResolve();
      return;
    }
    await patchWorkItem(id, { status: tr.toStatus });
    flash(
      t('workItem.transitionOk', {
        status: t(`status.${tr.toStatus}`),
      }),
    );
  };

  const handleResolve = async () => {
    if (!resolutionNotes.trim()) {
      setResolveError(t('workItem.resolutionRequired'));
      return;
    }
    await resolveWorkItem(id, resolutionNotes.trim());
    setResolveOpen(false);
    flash(t('workItem.resolvedToast'));
  };

  const sendComment = async () => {
    if (!comment.trim()) return;
    await addWorkItemComment(id, comment.trim(), { internal });
    setComment('');
    flash(t('workItem.commentSent'));
  };

  const onCommentKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      void sendComment();
    }
  };

  const saveImpact = async (value: ImpactLevel) => {
    setImpact(value);
    await patchWorkItem(id, { impact: value });
    flash(t('workItem.savedToast'));
  };

  const saveUrgency = async (value: UrgencyLevel) => {
    setUrgency(value);
    await patchWorkItem(id, { urgency: value });
    flash(t('workItem.savedToast'));
  };

  const saveService = async (value: string) => {
    await patchWorkItem(id, { service: value });
    flash(t('workItem.savedToast'));
  };

  const savePriority = async (value: string) => {
    await patchWorkItem(id, {
      priority: value as 'critical' | 'high' | 'medium' | 'low',
    });
    flash(t('workItem.savedToast'));
  };

  /** Persist a single form-engine field onto the work-item store. */
  const saveFormField = async (key: string, value: string) => {
    const trimmed = value.trim();
    // Skip no-op writes when value unchanged
    if (key === 'impact' && (wi?.impact ?? 'medium') === value) return;
    if (key === 'urgency' && (wi?.urgency ?? 'medium') === value) return;
    if (key === 'service' && wi?.service === value) return;
    if (key === 'title' && wi?.title === trimmed) return;
    if (key === 'description' && wi?.description === value) return;

    setFormValues((prev) => ({ ...prev, [key]: value }));
    if (key === 'impact') {
      await saveImpact(value as ImpactLevel);
      return;
    }
    if (key === 'urgency') {
      await saveUrgency(value as UrgencyLevel);
      return;
    }
    if (key === 'service') {
      await saveService(value);
      return;
    }
    if (key === 'title') {
      await patchWorkItem(id, { title: trimmed });
      flash(t('workItem.savedToast'));
      return;
    }
    if (key === 'description') {
      await patchWorkItem(id, { description: value });
      flash(t('workItem.savedToast'));
    }
  };

  const handleMacroMoreInfo = async () => {
    await patchWorkItem(id, { status: 'waiting' });
    await addWorkItemComment(id, MORE_INFO_TEMPLATE, { internal: false });
    flash(t('workItem.macroMoreInfoDone'));
    setTab('comments');
  };

  const watching = wi?.watchers?.some((p) => p.id === currentUser.id) ?? false;

  const toggleWatch = async () => {
    if (watching) {
      await unwatchWorkItem(id);
      flash(t('workItem.unwatchedToast'));
    } else {
      await watchWorkItem(id);
      flash(t('workItem.watchedToast'));
    }
  };

  if (item.loading) {
    return (
      <section className="page page--detail" aria-busy="true">
        <Skeleton height={28} width="40%" />
        <Skeleton height={16} width="60%" className="mt-2" />
        <div className="detail-layout mt-4">
          <Skeleton height={320} radius={12} />
          <Skeleton height={320} radius={12} />
        </div>
      </section>
    );
  }

  if (item.error && !item.data) {
    return (
      <section className="page">
        <ErrorState onRetry={item.reload} />
      </section>
    );
  }

  if (!wi) {
    return (
      <section className="page">
        <EmptyState
          title={t('workItem.notFound')}
          description={t('workItem.notFoundHint')}
          actionLabel={t('workItem.backToQueue')}
          onAction={() => navigate('/queues')}
        />
      </section>
    );
  }

  const assigned = wi.assignee?.id === currentUser.id;
  const resolved = wi.status === 'resolved' || wi.status === 'closed';
  const displayStatus: WorkItemStatus = wi.status;

  // Principal grants from mock RBAC role (u-anna → SERVICE_DESK_AGENT by default).
  // rbacTick re-reads after admin role reassignment.
  const principalPermissions =
    rbacTick >= 0 ? getUserPermissions(currentUser.id) : [];

  // Active workflow (session) → next transitions; falls back when inactive.
  // workflowTick invalidates after admin active-version toggle.
  const wfRuntime = getWorkItemRuntimeTransitions(wi, {
    definition:
      workflowTick >= 0 ? getActiveWorkflowDefinition('work-item') : null,
    permissions: principalPermissions,
  });
  const workflowStateLabel = (() => {
    const key = workflowStateLabelKey(wfRuntime.currentState);
    const labeled = t(key);
    return labeled === key ? wfRuntime.currentState : labeled;
  })();

  // Sticky Resolve only when matrix has RESOLVED edge from current state (S28).
  const resolveTransition = findResolveTransition(wfRuntime);
  const resolveDisabledReason = resolveTransition
    ? transitionDisabledReason(t, resolveTransition)
    : undefined;

  // Assign / Escalate: action-level permission stubs from RBAC catalog (S27).
  const assignMissingPerms = missingPermissionsFor(
    currentUser.id,
    [...WORK_ITEM_ACTION_PERMISSIONS.assign],
  );
  const escalateMissingPerms = missingPermissionsFor(
    currentUser.id,
    [...WORK_ITEM_ACTION_PERMISSIONS.escalate],
  );
  const assignPermReason = actionMissingPermissionReason(t, assignMissingPerms);
  const escalatePermReason = actionMissingPermissionReason(
    t,
    escalateMissingPerms,
  );
  const assignDisabled =
    assigned || resolved || assignMissingPerms.length > 0;
  const escalateDisabled =
    resolved || !!wi.escalated || escalateMissingPerms.length > 0;

  const relatedWorkItems = (allItems.data ?? []).filter((w) =>
    wi.relatedIds?.includes(w.id),
  );
  const relatedCis = (cis.data ?? []).filter((c) => wi.ciIds?.includes(c.id));
  const relatedKb = (kb.data ?? []).slice(0, 2);
  const relatedProblems = (problems.data ?? [])
    .filter((p) => p.priority === wi.priority || p.knownError)
    .slice(0, 2);
  const childTasks = wi.childTasks ?? [];
  const watchers = wi.watchers ?? [];

  const allComments = commentsQ.data ?? [];
  const progress = slaConsumedPct(wi.slaState, wi.slaTarget);
  const responseProgress =
    wi.slaState === 'breached' ? 100 : wi.slaState === 'at_risk' ? 88 : 55;

  const timeline = activity.data ?? [];

  const primaryTransitions = wfRuntime.transitions.filter(
    (tr) =>
      tr.toStatus === 'resolved' ||
      tr.toStatus === 'closed' ||
      tr.toStatus === 'in_progress',
  );
  const secondaryTransitions = wfRuntime.transitions.filter(
    (tr) => !primaryTransitions.includes(tr),
  );

  return (
    <section className="page page--detail">
      <div className="detail-sticky">
        <div className="detail-sticky__inner">
          <Link to="/queues" className="back-link back-link--inline">
            <ArrowLeft size={16} />
            {t('workItem.backToQueue')}
          </Link>
          <div className="detail-sticky__meta">
            <b className="mono accent">{wi.number}</b>
            <StatusChip status={displayStatus} />
            <span
              className="chip chip--workflow"
              title={
                wfRuntime.definition
                  ? t('workItem.workflowChipTitle', {
                      name: wfRuntime.definition.name ?? wfRuntime.definition.objectKey,
                      version: wfRuntime.definition.version,
                      state: wfRuntime.currentState,
                    })
                  : t('workItem.workflowFallbackChipTitle')
              }
            >
              <GitBranch size={12} aria-hidden />
              {workflowStateLabel}
              <span className="chip--workflow__key mono">
                {wfRuntime.currentState}
              </span>
            </span>
            <PriorityBadge priority={wi.priority} />
            <span className="type-pill">{t(`workItemType.${wi.type}`)}</span>
            {assigned && (
              <span className="chip">{t('workItem.assignedToYou')}</span>
            )}
            {wi.escalated && (
              <span className="chip chip--warn">{t('workItem.escalatedTag')}</span>
            )}
          </div>
          <div className="detail-sticky__actions">
            <Button
              variant="ghost"
              size="sm"
              icon={watching ? <EyeOff size={15} /> : <Eye size={15} />}
              onClick={() => void toggleWatch()}
              disabled={resolved}
            >
              {watching ? t('workItem.unwatch') : t('workItem.watch')}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              icon={<MessageCircleQuestion size={15} />}
              onClick={() => void handleMacroMoreInfo()}
              disabled={resolved || wi.status === 'waiting'}
            >
              {t('workItem.macroMoreInfo')}
            </Button>
            {escalatePermReason ? (
              <span className="work-item-workflow__tip" title={escalatePermReason}>
                <Button
                  variant="ghost"
                  size="sm"
                  icon={<ShieldAlert size={15} />}
                  onClick={() => void handleEscalate()}
                  disabled={escalateDisabled}
                  aria-disabled={escalateDisabled}
                >
                  {t('workItem.escalate')}
                </Button>
              </span>
            ) : (
              <Button
                variant="ghost"
                size="sm"
                icon={<ShieldAlert size={15} />}
                onClick={() => void handleEscalate()}
                disabled={escalateDisabled}
              >
                {t('workItem.escalate')}
              </Button>
            )}
            {assignPermReason ? (
              <span className="work-item-workflow__tip" title={assignPermReason}>
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<UserPlus size={15} />}
                  onClick={() => void handleAssign()}
                  disabled={assignDisabled}
                  aria-disabled={assignDisabled}
                >
                  {t('workItem.assignToMe')}
                </Button>
              </span>
            ) : (
              <Button
                variant="secondary"
                size="sm"
                icon={<UserPlus size={15} />}
                onClick={() => void handleAssign()}
                disabled={assignDisabled}
              >
                {t('workItem.assignToMe')}
              </Button>
            )}
            {resolveTransition &&
              (resolveDisabledReason ? (
                <span
                  className="work-item-workflow__tip"
                  title={resolveDisabledReason}
                >
                  <Button
                    variant="primary"
                    size="sm"
                    icon={<CheckCircle2 size={15} />}
                    onClick={openResolve}
                    disabled={!resolveTransition.enabled}
                    aria-disabled={!resolveTransition.enabled}
                  >
                    {t('workItem.resolve')}
                  </Button>
                </span>
              ) : (
                <Button
                  variant="primary"
                  size="sm"
                  icon={<CheckCircle2 size={15} />}
                  onClick={openResolve}
                  disabled={!resolveTransition.enabled}
                >
                  {t('workItem.resolve')}
                </Button>
              ))}
          </div>
        </div>
      </div>

      <div className="detail-title-row">
        <div>
          <h1>{wi.title}</h1>
          <p className="page-subtitle">
            {t('workItem.updated')}: {formatRelative(wi.updatedAt, t)} ·{' '}
            {t('workItem.created')}: {formatDateTime(wi.createdAt, locale)}
          </p>
        </div>
      </div>

      <div
        className="work-item-workflow"
        role="group"
        aria-label={t('workItem.workflow')}
      >
        <div className="work-item-workflow__head">
          <p className="work-item-workflow__label">
            <GitBranch size={14} aria-hidden />
            {t('workItem.workflow')}
          </p>
          <span className="work-item-workflow__meta muted">
            {wfRuntime.source === 'workflow' && wfRuntime.definition
              ? t('workItem.workflowSourceActive', {
                  name:
                    wfRuntime.definition.name ?? wfRuntime.definition.objectKey,
                  version: wfRuntime.definition.version,
                })
              : t('workItem.workflowSourceFallback')}
          </span>
        </div>
        {wfRuntime.transitions.length === 0 ? (
          <p className="work-item-workflow__empty muted">
            {t('workItem.workflowNoTransitions')}
          </p>
        ) : (
          <div className="module-workflow__stack">
            {primaryTransitions.length > 0 && (
              <div className="module-workflow__primary">
                {primaryTransitions.map((tr) => {
                  const reason = transitionDisabledReason(t, tr);
                  const btn = (
                    <Button
                      size="sm"
                      variant={transitionVariant(tr)}
                      disabled={!tr.enabled}
                      aria-disabled={!tr.enabled}
                      onClick={() => void handleWorkflowTransition(tr)}
                    >
                      {transitionActionLabel(t, tr)}
                    </Button>
                  );
                  return reason ? (
                    <span
                      key={tr.key}
                      className="work-item-workflow__tip"
                      title={reason}
                    >
                      {btn}
                    </span>
                  ) : (
                    <span key={tr.key}>{btn}</span>
                  );
                })}
              </div>
            )}
            {secondaryTransitions.length > 0 && (
              <div className="module-workflow__secondary">
                {secondaryTransitions.map((tr) => {
                  const reason = transitionDisabledReason(t, tr);
                  const btn = (
                    <Button
                      size="sm"
                      variant={transitionVariant(tr)}
                      disabled={!tr.enabled}
                      aria-disabled={!tr.enabled}
                      onClick={() => void handleWorkflowTransition(tr)}
                    >
                      {transitionActionLabel(t, tr)}
                    </Button>
                  );
                  return reason ? (
                    <span
                      key={tr.key}
                      className="work-item-workflow__tip"
                      title={reason}
                    >
                      {btn}
                    </span>
                  ) : (
                    <span key={tr.key}>{btn}</span>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </div>

      {(wi.slaState === 'breached' || wi.slaState === 'at_risk') && (
        <div
          className={`detail-banner detail-banner--${
            wi.slaState === 'breached' ? 'breach' : 'risk'
          }`}
          role="status"
        >
          {wi.slaState === 'breached' ? (
            <ShieldAlert size={18} aria-hidden />
          ) : (
            <AlertTriangle size={18} aria-hidden />
          )}
          <span>
            {wi.slaState === 'breached'
              ? t('workItem.breachBanner')
              : t('workItem.atRiskBanner')}
          </span>
          <b className="is-urgent">
            {wi.slaTarget} · {t(`sla.${wi.slaState}`)}
          </b>
        </div>
      )}

      <Tabs
        value={tab}
        onChange={setTab}
        items={[
          { id: 'details', label: t('workItem.details') },
          {
            id: 'activity',
            label: t('workItem.activityTab'),
            count: timeline.length,
          },
          {
            id: 'comments',
            label: t('workItem.comments'),
            count: allComments.length,
          },
          { id: 'related', label: t('workItem.related') },
          { id: 'sla', label: t('workItem.sla') },
        ]}
      />

      {tab === 'details' ? (
        <div className="detail-workbench">
          <section className="panel detail-panel">
            {formDef ? (
              <>
                <div className="section-head section-head--tight">
                  <h2>{t('workItem.fields')}</h2>
                  <span className="chip chip--muted" title={formDef.key}>
                    {t('form.engineChip', { v: formDef.version })}
                  </span>
                </div>
                <DynamicForm
                  definition={formDef}
                  values={formValues}
                  onChange={(key, value) =>
                    setFormValues((prev) => ({ ...prev, [key]: value }))
                  }
                  onCommit={(key, value) => void saveFormField(key, value)}
                  readOnly={resolved}
                  layout="detail"
                  optionLists={{
                    service: [
                      { value: wi.service, label: wi.service },
                      {
                        value: t('create.serviceWorkplace'),
                        label: t('create.serviceWorkplace'),
                      },
                      {
                        value: t('create.serviceAccess'),
                        label: t('create.serviceAccess'),
                      },
                      {
                        value: t('create.serviceApps'),
                        label: t('create.serviceApps'),
                      },
                    ].filter(
                      (o, i, arr) =>
                        arr.findIndex((x) => x.value === o.value) === i,
                    ),
                  }}
                />
              </>
            ) : (
              <>
                <h2>{t('workItem.description')}</h2>
                <p className="detail-body">{wi.description}</p>
              </>
            )}

            {wi.resolutionNotes && (
              <div className="resolution-notes">
                <span className="field__label">{t('workItem.resolutionNotes')}</span>
                <p>{wi.resolutionNotes}</p>
              </div>
            )}

            <h2 className="mt-4">{t('workItem.contextFields')}</h2>
            <div className="detail-fields detail-fields--enterprise">
              {!formDef && (
                <>
                  <Select
                    label={t('workItem.impact')}
                    value={impact}
                    onChange={(e) => void saveImpact(e.target.value as ImpactLevel)}
                    options={[
                      { value: 'high', label: t('workItem.impactHigh') },
                      { value: 'medium', label: t('workItem.impactMedium') },
                      { value: 'low', label: t('workItem.impactLow') },
                    ]}
                  />
                  <Select
                    label={t('workItem.urgency')}
                    value={urgency}
                    onChange={(e) =>
                      void saveUrgency(e.target.value as UrgencyLevel)
                    }
                    options={[
                      { value: 'high', label: t('workItem.urgencyHigh') },
                      { value: 'medium', label: t('workItem.urgencyMedium') },
                      { value: 'low', label: t('workItem.urgencyLow') },
                    ]}
                  />
                  <Select
                    label={t('workItem.service')}
                    value={wi.service}
                    onChange={(e) => void saveService(e.target.value)}
                    options={[
                      { value: wi.service, label: wi.service },
                      {
                        value: t('create.serviceWorkplace'),
                        label: t('create.serviceWorkplace'),
                      },
                      {
                        value: t('create.serviceAccess'),
                        label: t('create.serviceAccess'),
                      },
                      {
                        value: t('create.serviceApps'),
                        label: t('create.serviceApps'),
                      },
                    ]}
                  />
                </>
              )}
              <Select
                label={t('overview.colPriority')}
                value={wi.priority}
                onChange={(e) => void savePriority(e.target.value)}
                disabled={resolved}
                options={[
                  { value: 'critical', label: t('priority.critical') },
                  { value: 'high', label: t('priority.high') },
                  { value: 'medium', label: t('priority.medium') },
                  { value: 'low', label: t('priority.low') },
                ]}
              />
              <div className="field field--readonly">
                <span className="field__label">{t('workItem.queue')}</span>
                <div className="field__control field__control--static">
                  <span>{wi.queue ?? '—'}</span>
                </div>
              </div>
              <div className="field field--readonly">
                <span className="field__label">{t('workItem.requester')}</span>
                <div className="field__control field__control--static">
                  <span className="inline-person">
                    <Avatar initials={wi.requester.initials} size="sm" />
                    {wi.requester.name}
                  </span>
                </div>
              </div>
              <div className="field field--readonly">
                <span className="field__label">{t('workItem.assignee')}</span>
                <div className="field__control field__control--static">
                  <span className="inline-person">
                    {wi.assignee ? (
                      <>
                        <Avatar initials={wi.assignee.initials} size="sm" />
                        {wi.assignee.name}
                      </>
                    ) : (
                      t('overview.unassigned')
                    )}
                  </span>
                </div>
              </div>
            </div>

            {wi.tags && wi.tags.length > 0 && (
              <div className="tag-row">
                <span className="field__label">{t('workItem.tags')}</span>
                <div>
                  {wi.tags.map((tag) => (
                    <span className="chip" key={tag}>
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            )}

            <div className="detail-section mt-4">
              <div className="section-head section-head--tight">
                <h2>
                  <Paperclip size={16} aria-hidden /> {t('workItem.attachments')}
                </h2>
                <div>
                  <input
                    ref={attachInputRef}
                    type="file"
                    multiple
                    className="sr-only"
                    onChange={(e) => void handleAttach(e.target.files)}
                  />
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={<Plus size={14} />}
                    disabled={uploading || resolved}
                    onClick={() => attachInputRef.current?.click()}
                  >
                    {uploading ? t('workItem.uploading') : t('workItem.addAttachment')}
                  </Button>
                </div>
              </div>
              {attachments.length === 0 ? (
                <p className="muted">{t('workItem.noAttachments')}</p>
              ) : (
                <ul className="attachment-list" aria-label={t('workItem.attachments')}>
                  {attachments.map((att) => {
                    const safe =
                      !att.scanStatus ||
                      att.scanStatus === 'CLEAN' ||
                      att.scanStatus === 'SKIPPED';
                    return (
                    <li key={att.id} className="attachment-chip">
                      <Paperclip size={12} aria-hidden />
                      {safe ? (
                        <a
                          className="attachment-chip__name"
                          href={getContentUrl(att.id)}
                          target="_blank"
                          rel="noreferrer"
                          title={att.filename}
                        >
                          {att.filename}
                        </a>
                      ) : (
                        <span
                          className="attachment-chip__name attachment-chip__name--blocked"
                          title={att.scanDetail || att.scanStatus}
                        >
                          {att.filename}
                        </span>
                      )}
                      <span className="attachment-chip__size">
                        {formatBytes(att.size)}
                      </span>
                      {att.scanStatus && att.scanStatus !== 'CLEAN' && (
                        <span className={`attachment-scan attachment-scan--${att.scanStatus.toLowerCase()}`}>
                          {t(`workItem.scan_${att.scanStatus}`)}
                        </span>
                      )}
                      {!resolved && (
                        <button
                          type="button"
                          className="attachment-chip__remove"
                          aria-label={t('workItem.removeAttachment')}
                          onClick={() => void handleUnlink(att.id)}
                        >
                          <X size={12} />
                        </button>
                      )}
                    </li>
                    );
                  })}
                </ul>
              )}
            </div>

            <div className="detail-section mt-4">
              <h2>
                <ListTodo size={16} aria-hidden /> {t('workItem.childTasks')}
              </h2>
              {childTasks.length === 0 ? (
                <p className="muted">{t('workItem.noChildTasks')}</p>
              ) : (
                <ul className="child-task-list">
                  {childTasks.map((ct) => (
                    <li key={ct.id}>
                      <StatusChip status={ct.status} />
                      <span>{ct.title}</span>
                      {ct.assignee && (
                        <span className="inline-person muted">
                          <Avatar initials={ct.assignee.initials} size="sm" />
                          {ct.assignee.name}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="detail-section mt-4">
              <h2>
                <Eye size={16} aria-hidden /> {t('workItem.watchers')}
              </h2>
              {watchers.length === 0 ? (
                <p className="muted">{t('workItem.noWatchers')}</p>
              ) : (
                <ul className="watcher-list">
                  {watchers.map((p) => (
                    <li key={p.id}>
                      <Avatar initials={p.initials} size="sm" />
                      <span>{p.name}</span>
                      {p.role && <small className="muted">{p.role}</small>}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="detail-sla-inline">
              <span className="field__label">{t('sla.target')}</span>
              <div
                className={`detail-sla-inline__value${
                  wi.slaState === 'at_risk' || wi.slaState === 'breached'
                    ? ' is-urgent'
                    : ''
                }`}
              >
                <Clock3 size={14} aria-hidden />
                <b>
                  {wi.slaTarget} · {t(`sla.${wi.slaState}`)}
                </b>
                <div
                  className={`sla-bar sla-bar--sm sla-bar--${wi.slaState}`}
                  role="progressbar"
                  aria-valuenow={progress}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <i style={{ width: `${progress}%` }} />
                </div>
              </div>
            </div>
          </section>

          <aside className="panel detail-panel">
            <div className="section-head section-head--tight">
              <h2>{t('workItem.activityStream')}</h2>
              <button
                type="button"
                className="text-link"
                onClick={() => setTab('activity')}
              >
                {t('app.viewAll')} <ArrowRight size={14} />
              </button>
            </div>
            {timeline.length === 0 ? (
              <EmptyState title={t('workItem.noActivity')} />
            ) : (
              <ol className="timeline timeline--rich">
                {timeline.slice(0, 8).map((a) => {
                  const Icon = activityIcon(a.kind);
                  return (
                    <li
                      key={a.id}
                      className={`timeline__item timeline__item--${a.kind}`}
                    >
                      <span className="timeline__rail" aria-hidden>
                        <span className="timeline__dot">
                          <Icon size={12} />
                        </span>
                      </span>
                      <div>
                        <div className="timeline__head">
                          <b>{a.actor.name}</b>
                          <small>{formatRelative(a.at, t)}</small>
                        </div>
                        <p>{activityText(t, a.text)}</p>
                        <ActivityDiff
                          before={a.before}
                          after={a.after}
                          fieldLabel={(f) => activityFieldLabel(t, f)}
                        />
                      </div>
                    </li>
                  );
                })}
              </ol>
            )}

            <div className="comment-compose mt-4">
              <Textarea
                label={t('workItem.addComment')}
                rows={3}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                onKeyDown={onCommentKey}
                placeholder={t('workItem.commentPlaceholder')}
                hint={t('workItem.commentShortcut')}
              />
              <div className="comment-compose__actions">
                <label className="check-inline">
                  <input
                    type="checkbox"
                    checked={internal}
                    onChange={(e) => setInternal(e.target.checked)}
                  />
                  {internal
                    ? t('workItem.internalNote')
                    : t('workItem.publicComment')}
                </label>
                <Button
                  variant="primary"
                  icon={<MessageSquare size={15} />}
                  disabled={!comment.trim()}
                  onClick={() => void sendComment()}
                >
                  {t('workItem.send')}
                </Button>
              </div>
            </div>
          </aside>
        </div>
      ) : (
        <div className="detail-layout">
          <div className="detail-main">
            {tab === 'activity' && (
              <section className="panel detail-panel">
                {timeline.length === 0 ? (
                  <EmptyState title={t('workItem.noActivity')} />
                ) : (
                  <ol className="timeline timeline--rich">
                    {timeline.map((a) => {
                      const Icon = activityIcon(a.kind);
                      return (
                        <li
                          key={a.id}
                          className={`timeline__item timeline__item--${a.kind}`}
                        >
                          <span className="timeline__rail" aria-hidden>
                            <span className="timeline__dot">
                              <Icon size={12} />
                            </span>
                          </span>
                          <div>
                            <div className="timeline__head">
                              <Avatar initials={a.actor.initials} size="sm" />
                              <b>{a.actor.name}</b>
                              <span className="type-pill type-pill--sm">
                                {t(`workItem.activityKind.${a.kind}`)}
                              </span>
                              <small>{formatDateTime(a.at, locale)}</small>
                            </div>
                            <p>{activityText(t, a.text)}</p>
                            <ActivityDiff
                              before={a.before}
                              after={a.after}
                              fieldLabel={(f) => activityFieldLabel(t, f)}
                            />
                          </div>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </section>
            )}

            {tab === 'comments' && (
              <section className="panel detail-panel">
                {allComments.length === 0 ? (
                  <EmptyState
                    title={t('workItem.noComments')}
                    description={t('workItem.noCommentsHint')}
                  />
                ) : (
                  <ul className="comment-list">
                    {allComments.map((c) => (
                      <li key={c.id}>
                        <Avatar initials={c.author.initials} size="md" />
                        <div>
                          <div className="comment-head">
                            <b>{c.author.name}</b>
                            {c.internal && (
                              <span className="chip chip--warn">
                                {t('workItem.internalNote')}
                              </span>
                            )}
                            <small>{formatDateTime(c.at, locale)}</small>
                          </div>
                          <p>{c.body}</p>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}

                <div className="comment-compose">
                  <Textarea
                    label={t('workItem.addComment')}
                    rows={3}
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    onKeyDown={onCommentKey}
                    placeholder={t('workItem.commentPlaceholder')}
                    hint={t('workItem.commentShortcut')}
                  />
                  <div className="comment-compose__actions">
                    <label className="check-inline">
                      <input
                        type="checkbox"
                        checked={internal}
                        onChange={(e) => setInternal(e.target.checked)}
                      />
                      {internal
                        ? t('workItem.internalNote')
                        : t('workItem.publicComment')}
                    </label>
                    <Button
                      variant="primary"
                      icon={<MessageSquare size={15} />}
                      disabled={!comment.trim()}
                      onClick={() => void sendComment()}
                    >
                      {t('workItem.send')}
                    </Button>
                  </div>
                </div>
              </section>
            )}

            {tab === 'related' && (
              <section className="panel detail-panel related-panel">
                <RelatedBlock
                  icon={<ListTodo size={16} />}
                  title={t('workItem.childTasks')}
                  empty={childTasks.length === 0}
                  emptyLabel={t('workItem.noChildTasks')}
                >
                  <ul className="related-list">
                    {childTasks.map((ct) => (
                      <li key={ct.id}>
                        <b>{ct.title}</b>
                        <StatusChip status={ct.status} />
                      </li>
                    ))}
                  </ul>
                </RelatedBlock>

                <RelatedBlock
                  icon={<Boxes size={16} />}
                  title={t('workItem.relatedCi')}
                  empty={relatedCis.length === 0}
                  emptyLabel={t('workItem.noRelated')}
                >
                  <ul className="related-list">
                    {relatedCis.map((c) => (
                      <li key={c.id}>
                        <b>{c.name}</b>
                        <span>{t(c.kindKey)}</span>
                        <StatusChip status={c.status} />
                      </li>
                    ))}
                  </ul>
                </RelatedBlock>

                <RelatedBlock
                  icon={<Link2 size={16} />}
                  title={t('workItem.relatedWorkItems')}
                  empty={relatedWorkItems.length === 0}
                  emptyLabel={t('workItem.noRelated')}
                >
                  <ul className="related-list">
                    {relatedWorkItems.map((rel) => (
                      <li key={rel.id}>
                        <Link to={`/work-items/${rel.id}`} className="text-link">
                          <b className="mono accent">{rel.number}</b>
                        </Link>
                        <span>{rel.title}</span>
                        <StatusChip status={rel.status} />
                      </li>
                    ))}
                  </ul>
                </RelatedBlock>

                <RelatedBlock
                  icon={<ShieldAlert size={16} />}
                  title={t('workItem.relatedProblems')}
                  empty={relatedProblems.length === 0}
                  emptyLabel={t('workItem.noRelated')}
                >
                  <ul className="related-list">
                    {relatedProblems.map((p) => (
                      <li key={p.id}>
                        <b className="mono accent">{p.number}</b>
                        <span>{p.title}</span>
                        <PriorityBadge priority={p.priority} />
                      </li>
                    ))}
                  </ul>
                </RelatedBlock>

                <RelatedBlock
                  icon={<BookOpen size={16} />}
                  title={t('workItem.relatedKb')}
                  empty={relatedKb.length === 0}
                  emptyLabel={t('workItem.noRelated')}
                >
                  <ul className="related-list">
                    {relatedKb.map((a) => (
                      <li key={a.id}>
                        <Link to="/knowledge" className="text-link">
                          <b>{t(a.titleKey)}</b>
                        </Link>
                        <span>{t(a.summaryKey)}</span>
                      </li>
                    ))}
                  </ul>
                </RelatedBlock>
              </section>
            )}

            {tab === 'sla' && (
              <section className="panel detail-panel">
                <div className="sla-grid">
                  <div className="sla-card">
                    <span>{t('workItem.slaPolicy')}</span>
                    <b>{t(policyKey(wi.priority))}</b>
                  </div>
                  <div className={`sla-card sla-card--${wi.slaState}`}>
                    <span>{t('workItem.slaState')}</span>
                    <b>
                      {wi.slaState === 'breached' ? (
                        <ShieldAlert size={16} aria-hidden />
                      ) : wi.slaState === 'at_risk' ? (
                        <AlertTriangle size={16} aria-hidden />
                      ) : (
                        <Clock3 size={16} aria-hidden />
                      )}{' '}
                      {t(`sla.${wi.slaState}`)}
                    </b>
                  </div>
                </div>

                <div className="sla-progress-stack">
                  <SlaBar
                    label={t('workItem.slaResponse')}
                    value={responseProgress}
                    meta={t('sla.responseTarget', {
                      n: responseMins(wi.priority),
                    })}
                    state={wi.slaState}
                    remaining={wi.slaTarget}
                    t={t}
                  />
                  <SlaBar
                    label={t('workItem.slaResolution')}
                    value={progress}
                    meta={t('sla.resolutionTarget', {
                      n: resolutionHours(wi.priority),
                    })}
                    state={wi.slaState}
                    remaining={wi.slaTarget}
                    t={t}
                  />
                </div>

                <div className="sla-card sla-card--wide mt-4">
                  <span>
                    <Clock3 size={14} aria-hidden /> {t('workItem.slaClock')}
                  </span>
                  <b
                    className={
                      wi.slaState === 'at_risk' || wi.slaState === 'breached'
                        ? 'is-urgent'
                        : ''
                    }
                  >
                    {wi.slaTarget} · {t(`sla.${wi.slaState}`)}
                  </b>
                </div>
              </section>
            )}
          </div>

          <aside className="detail-side">
            <section className="panel detail-side-card">
              <h3>{t('workItem.details')}</h3>
              <dl className="side-dl">
                <div>
                  <dt>{t('workItem.service')}</dt>
                  <dd>{wi.service}</dd>
                </div>
                <div>
                  <dt>{t('workItem.queue')}</dt>
                  <dd>{wi.queue ?? '—'}</dd>
                </div>
                <div>
                  <dt>{t('workItem.impact')}</dt>
                  <dd>
                    {impact === 'high'
                      ? t('workItem.impactHigh')
                      : impact === 'medium'
                        ? t('workItem.impactMedium')
                        : t('workItem.impactLow')}
                  </dd>
                </div>
                <div>
                  <dt>{t('workItem.urgency')}</dt>
                  <dd>
                    {urgency === 'high'
                      ? t('workItem.urgencyHigh')
                      : urgency === 'medium'
                        ? t('workItem.urgencyMedium')
                        : t('workItem.urgencyLow')}
                  </dd>
                </div>
                <div>
                  <dt>{t('workItem.requester')}</dt>
                  <dd className="inline-person">
                    <Avatar initials={wi.requester.initials} size="sm" />
                    {wi.requester.name}
                  </dd>
                </div>
                <div>
                  <dt>{t('workItem.assignee')}</dt>
                  <dd className="inline-person">
                    {wi.assignee ? (
                      <>
                        <Avatar initials={wi.assignee.initials} size="sm" />
                        {wi.assignee.name}
                      </>
                    ) : (
                      t('overview.unassigned')
                    )}
                  </dd>
                </div>
                <div>
                  <dt>{t('workItem.watchers')}</dt>
                  <dd>{watchers.length}</dd>
                </div>
                <div>
                  <dt>{t('sla.target')}</dt>
                  <dd
                    className={
                      wi.slaState === 'at_risk' || wi.slaState === 'breached'
                        ? 'is-urgent'
                        : ''
                    }
                  >
                    <Clock3 size={14} aria-hidden style={{ verticalAlign: -2 }} />{' '}
                    {wi.slaTarget} · {t(`sla.${wi.slaState}`)}
                  </dd>
                </div>
              </dl>

              <div className="side-sla-mini">
                <div className="side-sla-mini__label">
                  <span>{t('workItem.slaResolution')}</span>
                  <b className={wi.slaState === 'at_risk' ? 'is-urgent' : ''}>
                    {progress}%
                  </b>
                </div>
                <div
                  className={`sla-bar sla-bar--${wi.slaState}`}
                  role="progressbar"
                  aria-valuenow={progress}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <i style={{ width: `${progress}%` }} />
                </div>
              </div>
            </section>

            <section className="panel detail-side-card">
              <h3>{t('workItem.related')}</h3>
              <ul className="side-related">
                <li>
                  <ListTodo size={14} aria-hidden />
                  <span>
                    {t('workItem.childTasks')} · {childTasks.length}
                  </span>
                </li>
                <li>
                  <Boxes size={14} aria-hidden />
                  <span>
                    {t('workItem.relatedCi')} · {relatedCis.length}
                  </span>
                </li>
                <li>
                  <Link2 size={14} aria-hidden />
                  <span>
                    {t('workItem.relatedWorkItems')} · {relatedWorkItems.length}
                  </span>
                </li>
                <li>
                  <BookOpen size={14} aria-hidden />
                  <span>
                    {t('workItem.relatedKb')} · {relatedKb.length}
                  </span>
                </li>
              </ul>
              <button
                type="button"
                className="text-link mt-2"
                onClick={() => setTab('related')}
              >
                {t('workItem.openRelated')} <ArrowRight size={14} />
              </button>
            </section>
          </aside>
        </div>
      )}

      <Modal
        open={resolveOpen}
        onClose={() => setResolveOpen(false)}
        size="md"
        labelledBy="resolve-title"
      >
        <h2 id="resolve-title">{t('workItem.resolveTitle')}</h2>
        <p className="muted mb-2">{t('workItem.resolveHint')}</p>
        <Textarea
          label={t('workItem.resolutionNotes')}
          rows={4}
          value={resolutionNotes}
          onChange={(e) => {
            setResolutionNotes(e.target.value);
            setResolveError('');
          }}
          error={resolveError}
          placeholder={t('workItem.resolutionPlaceholder')}
        />
        <div className="modal-actions mt-4">
          <Button variant="secondary" onClick={() => setResolveOpen(false)}>
            {t('app.cancel')}
          </Button>
          <Button
            variant="primary"
            icon={<CheckCircle2 size={15} />}
            onClick={() => void handleResolve()}
          >
            {t('workItem.resolve')}
          </Button>
        </div>
      </Modal>
    </section>
  );
}

function RelatedBlock({
  icon,
  title,
  empty,
  emptyLabel,
  children,
}: {
  icon: ReactNode;
  title: string;
  empty: boolean;
  emptyLabel: string;
  children: ReactNode;
}) {
  return (
    <div className="related-block">
      <h2>
        {icon}
        {title}
      </h2>
      {empty ? <p className="muted">{emptyLabel}</p> : children}
    </div>
  );
}

function SlaBar({
  label,
  value,
  meta,
  state,
  remaining,
  t,
}: {
  label: string;
  value: number;
  meta: string;
  state: SlaState;
  remaining: string;
  t: (k: string, v?: Record<string, string | number>) => string;
}) {
  const urgency =
    state === 'breached' ? 'breached' : state === 'at_risk' ? 'at_risk' : 'on_track';
  return (
    <div className="sla-progress">
      <div className="sla-progress__head">
        <span>{label}</span>
        <b>
          {meta} · {t(`sla.${state}`)}
        </b>
      </div>
      <div
        className={`sla-bar sla-bar--${urgency}`}
        role="progressbar"
        aria-valuenow={value}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}
      >
        <i style={{ width: `${value}%` }} />
      </div>
      <small>
        {t('sla.remaining')}: {remaining}
      </small>
    </div>
  );
}

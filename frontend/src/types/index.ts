export type Priority = 'critical' | 'high' | 'medium' | 'low';
export type WorkItemType = 'incident' | 'request' | 'change' | 'problem';
export type WorkItemStatus =
  | 'new'
  | 'in_progress'
  | 'waiting'
  | 'resolved'
  | 'closed'
  | 'cancelled';

export type SlaState = 'on_track' | 'at_risk' | 'breached' | 'met';

export type ImpactLevel = 'high' | 'medium' | 'low';
export type UrgencyLevel = 'high' | 'medium' | 'low';

export interface Person {
  id: string;
  name: string;
  initials: string;
  role?: string;
  email?: string;
  teamId?: string;
}

export interface ChildTask {
  id: string;
  title: string;
  status: WorkItemStatus;
  assignee: Person | null;
}

export interface WorkItem {
  id: string;
  number: string;
  title: string;
  description: string;
  type: WorkItemType;
  priority: Priority;
  status: WorkItemStatus;
  assignee: Person | null;
  requester: Person;
  service: string;
  slaTarget: string;
  slaState: SlaState;
  updatedAt: string;
  createdAt: string;
  queue?: string;
  tags?: string[];
  relatedIds?: string[];
  ciIds?: string[];
  /** Assignment group / team for queue predicates */
  teamId?: string;
  impact?: ImpactLevel;
  urgency?: UrgencyLevel;
  /** First-class escalation flag written by escalate action */
  escalated?: boolean;
  watchers?: Person[];
  childTasks?: ChildTask[];
  resolutionNotes?: string;
}

export interface WorkItemActivity {
  id: string;
  at: string;
  actor: Person;
  kind: 'status' | 'comment' | 'assignment' | 'system' | 'sla' | 'field';
  text: string;
  /** Optional field-level before snapshot for audit-style diffs */
  before?: Record<string, unknown> | null;
  /** Optional field-level after snapshot for audit-style diffs */
  after?: Record<string, unknown> | null;
}

/** Platform / admin audit event (mock list for /admin/audit) */
export interface AuditEvent {
  id: string;
  at: string;
  actor: Person;
  /** Stable action key: create | update | assign | resolve | escalate | comment | login | config | delete */
  action: string;
  objectType: string;
  objectId?: string;
  objectLabel?: string;
  detail?: string;
}

export interface WorkItemComment {
  id: string;
  at: string;
  author: Person;
  body: string;
  internal?: boolean;
}

export interface CatalogService {
  id: string;
  titleKey: string;
  descriptionKey: string;
  metaKey: string;
  categoryId: string;
  icon: 'key' | 'laptop' | 'monitor' | 'shield' | 'cloud' | 'server';
  popular?: boolean;
  approvalRequired?: boolean;
}

export interface CatalogCategory {
  id: string;
  titleKey: string;
  descriptionKey: string;
  count: number;
  icon: 'key' | 'laptop' | 'monitor' | 'shield';
  tone: 'lilac' | 'blue' | 'mint' | 'coral';
}

export type KnowledgeArticleStatus = 'published' | 'pending';

export interface KnowledgeArticle {
  id: string;
  titleKey: string;
  summaryKey: string;
  tagKey: string;
  readMinutes: number;
  helpfulScore: number;
  /** Absolute yes-votes (session-mutable) */
  helpfulYes?: number;
  /** Absolute no-votes (session-mutable) */
  helpfulNo?: number;
  /** Current operator vote in this session */
  userVote?: 'yes' | 'no';
  verified: boolean;
  icon: 'key' | 'shield' | 'laptop' | 'book';
  topicId: string;
  updatedAt: string;
  /** Plain title for contributed / edited articles (bypasses i18n keys) */
  title?: string;
  summary?: string;
  body?: string;
  /** Plain tag override for contributed / edited articles */
  tag?: string;
  status?: KnowledgeArticleStatus;
  /** Operator note describing the latest edit */
  versionNote?: string;
  /** Monotonic edit/publish counter (mock CMS) */
  version?: number;
}

export interface CreateKnowledgeArticlePayload {
  title: string;
  body: string;
  topicId?: string;
  status?: KnowledgeArticleStatus;
  tag?: string;
}

export interface UpdateKnowledgeArticlePayload {
  title?: string;
  body?: string;
  tag?: string;
  versionNote?: string;
  status?: KnowledgeArticleStatus;
}

export interface KnowledgeTopic {
  id: string;
  titleKey: string;
  count: number;
}

export type CiStatus = 'operational' | 'degraded' | 'maintenance' | 'retired';
export type CiIcon = 'server' | 'cloud' | 'network' | 'database' | 'app';
export type CiRelationType =
  | 'depends_on'
  | 'hosted_on'
  | 'runs_on'
  | 'uses'
  | 'connects_to'
  /** @deprecated use hosted_on — kept for hydrated session stores */
  | 'hosts';

export interface ConfigurationItem {
  id: string;
  version?: number;
  name: string;
  kindKey: string;
  status: CiStatus;
  owner: string;
  icon: CiIcon;
  tone: 'violet' | 'cyan' | 'amber' | 'mint';
  environment?: string;
  criticality?: Priority;
}

/** Directed edge in the CMDB dependency graph */
export interface CiRelation {
  id: string;
  fromId: string;
  toId: string;
  type: CiRelationType;
}

/** Impact analysis node (1–2 hop) for a planned change */
export interface CiImpactEntry {
  ciId: string;
  hop: 1 | 2;
  impact: ImpactLevel;
  usersAffected?: number;
  serviceKey?: string;
}

export type AssetStatus = 'in_use' | 'stock' | 'repair' | 'retired';
export type AssetTypeKey =
  | 'assets.types.laptop'
  | 'assets.types.monitor'
  | 'assets.types.phone'
  | 'assets.types.peripheral';

export interface Asset {
  id: string;
  version?: number;
  tag: string;
  name: string;
  typeKey: string;
  status: AssetStatus;
  assignedTo: string | null;
  location: string;
  purchasedAt: string;
  serial?: string;
  model?: string;
  vendor?: string;
  costCenter?: string;
  notes?: string;
  relatedCiIds?: string[];
  updatedAt?: string;
}

export interface Problem {
  id: string;
  version?: number;
  number: string;
  title: string;
  status: WorkItemStatus;
  priority: Priority;
  knownError: boolean;
  relatedIncidents: number;
  assignee: Person | null;
  updatedAt: string;
  createdAt?: string;
  description?: string;
  rootCause?: string;
  workaround?: string;
  resolution?: string;
  service?: string;
  relatedWorkItemIds?: string[];
  relatedCiIds?: string[];
}

export type ChangeType = 'standard' | 'normal' | 'emergency';
export type ChangeStatus =
  | 'draft'
  | 'scheduled'
  | 'in_progress'
  | 'completed'
  | 'cancelled'
  | 'cab_review';

export type CabVoteDecision = 'approve' | 'reject' | 'abstain';

export interface CabVote {
  memberId: string;
  memberName: string;
  initials: string;
  role?: string;
  decision?: CabVoteDecision;
  at?: string;
}

export interface Change {
  id: string;
  number: string;
  title: string;
  type: ChangeType;
  status: ChangeStatus;
  risk: Priority;
  plannedStart: string;
  plannedEnd: string;
  assignee: Person | null;
  updatedAt: string;
  createdAt?: string;
  description?: string;
  implementationPlan?: string;
  backoutPlan?: string;
  service?: string;
  cabApproved?: boolean;
  cabRejected?: boolean;
  cabNotes?: string;
  cabVotes?: CabVote[];
  relatedWorkItemIds?: string[];
  relatedCiIds?: string[];
}

/** Shared activity/history entry for secondary modules (assets / problems / changes) */
export interface ModuleActivity {
  id: string;
  at: string;
  actor: Person;
  kind: 'status' | 'field' | 'system' | 'comment';
  /** i18n key under module.activity.* or plain label */
  textKey: string;
  detail?: string;
}

export interface CreateAssetPayload {
  tag: string;
  name: string;
  typeKey: string;
  status?: AssetStatus;
  assignedTo?: string | null;
  location: string;
  serial?: string;
  model?: string;
  vendor?: string;
  notes?: string;
  /** ISO date (yyyy-MM-dd) when asset was acquired */
  purchasedAt?: string;
}

export interface CreateProblemPayload {
  title: string;
  description?: string;
  priority?: Priority;
  service?: string;
  knownError?: boolean;
  rootCause?: string;
  workaround?: string;
  resolution?: string;
}

export interface CreateChangePayload {
  title: string;
  description?: string;
  type?: ChangeType;
  risk?: Priority;
  service?: string;
  plannedStart?: string;
  plannedEnd?: string;
  implementationPlan?: string;
  backoutPlan?: string;
}

export interface DashboardMetrics {
  open: number;
  openDelta: number;
  dueToday: number;
  dueUrgent: number;
  breached: number;
  satisfaction: number;
  flow: {
    new: number;
    inProgress: number;
    waiting: number;
  };
}

export interface NotificationPrefs {
  email: boolean;
  desktop: boolean;
  slaAlerts: boolean;
  assignment: boolean;
  mentions: boolean;
}

export interface UserProfile {
  id: string;
  name: string;
  email: string;
  role: string;
  team: string;
  teamId: string;
  initials: string;
  timezone: string;
}

export type LocaleCode = 'ru' | 'en' | 'de';

export type CreateKind = 'incident' | 'request';

export interface CreateWorkItemPayload {
  kind: CreateKind;
  title: string;
  description: string;
  service: string;
  priority?: Priority;
  impact?: ImpactLevel;
  urgency?: UrgencyLevel;
  queue?: string;
  teamId?: string;
}

/** Saved queue view (filters snapshot) */
export interface QueueSavedView {
  id: string;
  name: string;
  tab: string;
  priority: string;
  type: string;
  status: string;
  sla: string;
  builtin?: boolean;
}

/** Matches backend AutomationRule.Operator */
export type AutomationOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'CONTAINS'
  | 'GREATER_THAN';

/**
 * Declarative automation rule: WHEN event IF conditions THEN actions.
 * Aligned with backend `AutomationRule` (platform.automation).
 */
export interface AutomationRule {
  id: string;
  ruleKey: string;
  name: string;
  version: number;
  enabled: boolean;
  trigger: { eventType: string };
  conditions: AutomationCondition[];
  actions: AutomationAction[];
  /** Optional UI description (not persisted on backend) */
  description?: string;
}

export interface AutomationCondition {
  field: string;
  operator: AutomationOperator;
  value: string;
}

export interface AutomationAction {
  type: string;
  parameters: Record<string, unknown>;
}

/**
 * Deterministic workflow contract.
 * Aligned with backend `WorkflowDefinition` (platform.workflow).
 */
export interface WorkflowTransition {
  key: string;
  from: string;
  to: string;
    requiredPermissions: string[];
    requiredFields: string[];
    conditions?: Array<{
      field: string;
      operator: 'EQUALS' | 'NOT_EQUALS' | 'IN' | 'CONTAINS' | 'EXISTS' | 'GT' | 'GTE' | 'LT' | 'LTE';
      value: unknown;
    }>;
    approval?: {
    mode: 'ANY' | 'ALL' | 'QUORUM';
    voterRoles: string[];
      quorum?: number;
    };
    timer?: { delaySeconds: number; maxAttempts: number };
}

export interface WorkflowDefinition {
  id: string;
  objectKey: string;
  version: number;
  /** Session-mutable: only one active version per objectKey */
  active: boolean;
  initialState: string;
  states: string[];
  transitions: WorkflowTransition[];
  /** Optional UI label (not on backend record) */
  name?: string;
  description?: string;
}

/**
 * SLA policy definition.
 * Aligned with backend `SlaPolicy` — targets use hours in the mock admin UI
 * (backend stores Duration / minutes).
 */
export interface SlaTarget {
  metric: 'response' | 'resolution' | string;
  /** e.g. priority=CRITICAL */
  condition: string;
  /** Target duration in hours (editable in mock store) */
  targetHours: number;
  /** Warning lead time in hours */
  warningBeforeHours: number;
}

export interface SlaPolicy {
  id: string;
  key: string;
  calendarKey: string;
  enabled: boolean;
  version: number;
  targets: SlaTarget[];
  pauseStates: string[];
  /** Optional UI label */
  name?: string;
  description?: string;
}

/** Explicit business-time calendar (backend WorkingCalendar). */
export interface WorkingCalendarMock {
  key: string;
  zone: string;
  workingDays: string[];
  startsAt: string;
  endsAt: string;
  holidays: string[];
}

/**
 * Platform RBAC — aligned with backend `role` / `permission` / `role_permission`
 * (Flyway V10+). Role keys match Keycloak realm roles.
 */
export type RbacRoleKey =
  | 'ADMIN'
  | 'SERVICE_DESK_AGENT'
  | 'SERVICE_DESK_MANAGER'
  | 'REQUESTER'
  | 'CHANGE_MANAGER'
  | 'CAB_MEMBER';

export type RbacUserStatus = 'active' | 'inactive' | 'locked';

export interface RbacPermission {
  key: string;
  description: string;
}

export interface RbacRole {
  id: string;
  roleKey: RbacRoleKey;
  /** i18n-friendly labels (en/ru; de falls back to en in UI) */
  labels: { en: string; ru: string; de?: string };
  description: string;
  /** Permission keys granted to this role (read-only catalog) */
  permissions: string[];
}

/** Mock directory user for RBAC admin (session-mutable role assignment). */
export interface RbacUser {
  id: string;
  name: string;
  email: string;
  initials: string;
  roleKey: RbacRoleKey;
  locale: LocaleCode;
  status: RbacUserStatus;
  /** Optional fixed Keycloak `sub` for demo principals */
  subjectId?: string;
}

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
  /** Plain title for contributed articles (bypasses i18n keys) */
  title?: string;
  summary?: string;
  body?: string;
  status?: KnowledgeArticleStatus;
}

export interface CreateKnowledgeArticlePayload {
  title: string;
  body: string;
  topicId?: string;
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
  | 'runs_on'
  | 'uses'
  | 'connects_to'
  | 'hosts';

export interface ConfigurationItem {
  id: string;
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
}

export interface CreateProblemPayload {
  title: string;
  description?: string;
  priority?: Priority;
  service?: string;
  knownError?: boolean;
  rootCause?: string;
  workaround?: string;
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

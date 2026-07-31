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

export interface KnowledgeArticle {
  id: string;
  titleKey: string;
  summaryKey: string;
  tagKey: string;
  readMinutes: number;
  helpfulScore: number;
  verified: boolean;
  icon: 'key' | 'shield' | 'laptop' | 'book';
  topicId: string;
  updatedAt: string;
}

export interface KnowledgeTopic {
  id: string;
  titleKey: string;
  count: number;
}

export interface ConfigurationItem {
  id: string;
  name: string;
  kindKey: string;
  status: 'operational' | 'degraded' | 'maintenance' | 'retired';
  owner: string;
  icon: 'server' | 'cloud' | 'network' | 'database' | 'app';
  tone: 'violet' | 'cyan' | 'amber' | 'mint';
  environment?: string;
  criticality?: Priority;
}

export interface Asset {
  id: string;
  tag: string;
  name: string;
  typeKey: string;
  status: 'in_use' | 'stock' | 'repair' | 'retired';
  assignedTo: string | null;
  location: string;
  purchasedAt: string;
  serial?: string;
  model?: string;
  vendor?: string;
  costCenter?: string;
  notes?: string;
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
  description?: string;
  rootCause?: string;
  workaround?: string;
  service?: string;
}

export interface Change {
  id: string;
  number: string;
  title: string;
  type: 'standard' | 'normal' | 'emergency';
  status: 'draft' | 'scheduled' | 'in_progress' | 'completed' | 'cancelled' | 'cab_review';
  risk: Priority;
  plannedStart: string;
  plannedEnd: string;
  assignee: Person | null;
  updatedAt: string;
  description?: string;
  implementationPlan?: string;
  backoutPlan?: string;
  service?: string;
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

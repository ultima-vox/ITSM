import type { LucideIcon } from 'lucide-react';
import {
  BookOpen,
  Boxes,
  ClipboardList,
  Database,
  Gauge,
  GitBranch,
  Grid2X2,
  LayoutDashboard,
  Megaphone,
  Package,
  PhoneCall,
  Rocket,
  ScrollText,
  Search,
  Settings,
  Shield,
  TicketCheck,
  AlertOctagon,
  Timer,
  Workflow,
  Zap,
} from 'lucide-react';
import type { Experience } from '@/app/experiences';

export interface NavItem {
  to: string;
  key: string;
  icon: LucideIcon;
  end?: boolean;
  liveBadge?: boolean;
}

export const operatorNav: readonly NavItem[] = [
  { to: '/', key: 'overview', icon: LayoutDashboard, end: true },
  { to: '/my-work', key: 'myWork', icon: TicketCheck, liveBadge: true },
  { to: '/queues', key: 'queues', icon: Grid2X2 },
  { to: '/search', key: 'search', icon: Search },
  { to: '/catalog', key: 'catalog', icon: ClipboardList },
  { to: '/knowledge', key: 'knowledge', icon: BookOpen },
  { to: '/cmdb', key: 'cmdb', icon: Boxes },
  { to: '/assets', key: 'assets', icon: Package },
  { to: '/problems', key: 'problems', icon: AlertOctagon },
  { to: '/changes', key: 'changes', icon: GitBranch },
  { to: '/releases', key: 'releases', icon: Rocket },
];

export const adminNav: readonly NavItem[] = [
  { to: '/admin/metadata', key: 'metadata', icon: Database },
  { to: '/admin/automation', key: 'automation', icon: Zap },
  { to: '/admin/workflow', key: 'workflow', icon: Workflow },
  { to: '/admin/sla', key: 'sla', icon: Timer },
  { to: '/admin/rbac', key: 'rbac', icon: Shield },
  { to: '/admin/oncall', key: 'oncall', icon: PhoneCall },
  { to: '/admin/announcements', key: 'announcements', icon: Megaphone },
  { to: '/admin/audit', key: 'audit', icon: ScrollText },
];

export const portalNav: readonly NavItem[] = [
  { to: '/portal/catalog', key: 'catalog', icon: ClipboardList },
  { to: '/portal/knowledge', key: 'knowledge', icon: BookOpen },
  { to: '/portal/requests', key: 'myRequests', icon: TicketCheck, liveBadge: true },
];

export const operatorSecondaryNav: readonly NavItem[] = [
  { to: '/reports', key: 'reports', icon: Gauge },
  { to: '/settings', key: 'settings', icon: Settings },
];

export const adminSecondaryNav: readonly NavItem[] = [
  { to: '/settings', key: 'settings', icon: Settings },
];

export const crumbKeys: Record<string, string> = {
  '/': 'nav.overview',
  '/my-work': 'nav.myWork',
  '/queues': 'nav.queues',
  '/catalog': 'nav.catalog',
  '/knowledge': 'nav.knowledge',
  '/cmdb': 'nav.cmdb',
  '/assets': 'nav.assets',
  '/problems': 'nav.problems',
  '/changes': 'nav.changes',
  '/reports': 'nav.reports',
  '/settings': 'nav.settings',
  '/admin/metadata': 'nav.metadata',
  '/admin/automation': 'nav.automation',
  '/admin/workflow': 'nav.workflow',
  '/admin/sla': 'nav.sla',
  '/admin/rbac': 'nav.rbac',
  '/admin/audit': 'nav.audit',
  '/admin/oncall': 'nav.oncall',
  '/admin/announcements': 'nav.announcements',
  '/releases': 'nav.releases',
  '/search': 'nav.search',
  '/notifications': 'nav.notifications',
  '/portal': 'nav.catalog',
  '/portal/catalog': 'nav.catalog',
  '/portal/knowledge': 'nav.knowledge',
  '/portal/requests': 'nav.myRequests',
};

export function navFor(experience: Experience): readonly NavItem[] {
  if (experience === 'admin') return adminNav;
  if (experience === 'portal') return portalNav;
  return operatorNav;
}

export function secondaryNavFor(experience: Experience): readonly NavItem[] {
  if (experience === 'admin') return adminSecondaryNav;
  if (experience === 'operator') return operatorSecondaryNav;
  return [];
}

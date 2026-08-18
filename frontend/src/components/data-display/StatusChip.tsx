import {
  Circle,
  CircleDot,
  Clock3,
  CheckCircle2,
  XCircle,
  Ban,
  AlertCircle,
  Wrench,
  Package,
} from 'lucide-react';
import { useT } from '@/i18n';

const iconMap: Record<string, typeof Circle> = {
  new: CircleDot,
  in_progress: Circle,
  waiting: Clock3,
  resolved: CheckCircle2,
  closed: XCircle,
  cancelled: Ban,
  draft: Circle,
  submitted: CircleDot,
  cab_review: AlertCircle,
  approved: CheckCircle2,
  scheduled: Clock3,
  implementing: Circle,
  review: AlertCircle,
  completed: CheckCircle2,
  operational: CheckCircle2,
  degraded: AlertCircle,
  maintenance: Wrench,
  retired: Ban,
  in_use: CheckCircle2,
  stock: Package,
  repair: Wrench,
};

const toneMap: Record<string, string> = {
  new: 'blue',
  in_progress: 'violet',
  waiting: 'amber',
  resolved: 'mint',
  closed: 'neutral',
  cancelled: 'neutral',
  draft: 'neutral',
  submitted: 'blue',
  cab_review: 'amber',
  approved: 'mint',
  scheduled: 'blue',
  implementing: 'violet',
  review: 'amber',
  completed: 'mint',
  operational: 'mint',
  degraded: 'amber',
  maintenance: 'blue',
  retired: 'neutral',
  in_use: 'mint',
  stock: 'blue',
  repair: 'amber',
};

export function StatusChip({ status }: { status: string }) {
  const t = useT();
  const Icon = iconMap[status] ?? Circle;
  const tone = toneMap[status] ?? 'neutral';
  const label = t(`status.${status}`);
  return (
    <span className={`status-chip status-chip--${tone}`}>
      <Icon size={12} aria-hidden />
      <span>{label}</span>
    </span>
  );
}

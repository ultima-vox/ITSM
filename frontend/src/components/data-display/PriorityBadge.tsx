import { AlertTriangle, ArrowUp, Minus, ArrowDown } from 'lucide-react';
import type { Priority } from '@/types';
import { useT } from '@/i18n';

const tone: Record<Priority, string> = {
  critical: 'critical',
  high: 'high',
  medium: 'medium',
  low: 'low',
};

const icons: Record<Priority, typeof AlertTriangle> = {
  critical: AlertTriangle,
  high: ArrowUp,
  medium: Minus,
  low: ArrowDown,
};

export function PriorityBadge({ priority }: { priority: Priority }) {
  const t = useT();
  const Icon = icons[priority];
  return (
    <span className={`priority priority--${tone[priority]}`}>
      <Icon size={12} aria-hidden />
      <span>{t(`priority.${priority}`)}</span>
    </span>
  );
}

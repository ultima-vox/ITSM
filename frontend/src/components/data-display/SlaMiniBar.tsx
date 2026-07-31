import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  ShieldAlert,
} from 'lucide-react';
import type { SlaState } from '@/types';
import { useT } from '@/i18n';
import { slaConsumedPct, slaTimeLabel } from '@/lib/sla';

interface SlaMiniBarProps {
  state: SlaState;
  target: string;
  compact?: boolean;
}

export function SlaMiniBar({ state, target, compact }: SlaMiniBarProps) {
  const t = useT();
  const pct = slaConsumedPct(state, target);
  const urgent = state === 'at_risk' || state === 'breached';
  const Icon =
    state === 'breached'
      ? ShieldAlert
      : state === 'at_risk'
        ? AlertTriangle
        : state === 'met'
          ? CheckCircle2
          : Clock3;

  return (
    <div
      className={`sla-mini${urgent ? ' is-urgent' : ''}${compact ? ' sla-mini--compact' : ''}`}
      title={`${t(`sla.${state}`)} · ${slaTimeLabel(target, t)} · ${pct}%`}
    >
      <div className="sla-mini__row">
        <Icon size={13} aria-hidden />
        {!compact && (
          <span className="sla-mini__label">{t(`sla.${state}`)}</span>
        )}
        <span className="sla-mini__time">{slaTimeLabel(target, t)}</span>
      </div>
      <div
        className={`sla-bar sla-bar--sm sla-bar--${state}`}
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={t(`sla.${state}`)}
      >
        <i style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

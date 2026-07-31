import type { SlaState } from '@/types';

/** Approximate consumed % of SLA window for mini progress bars (mock-friendly). */
export function slaConsumedPct(state: SlaState, target: string): number {
  if (state === 'met' || state === 'breached') return 100;
  if (state === 'at_risk') return 82;
  if (target.includes(':')) {
    const [a, b] = target.split(':').map(Number);
    const mins = (a || 0) * 60 + (b || 0);
    return Math.round(Math.max(8, Math.min(92, 100 - (mins / 240) * 100)));
  }
  if (target === 'tomorrow' || target === '1d' || target === '2d') return 28;
  return 45;
}

export function slaTimeLabel(
  target: string,
  t: (k: string, v?: Record<string, string | number>) => string,
): string {
  if (target === 'tomorrow') return t('sla.tomorrow');
  if (target === 'met') return t('sla.met');
  if (target === '1d') return t('sla.days', { n: 1 });
  if (target === '2d') return t('sla.days', { n: 2 });
  return target;
}

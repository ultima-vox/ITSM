import type { ReactNode } from 'react';

interface MetricCardProps {
  icon: ReactNode;
  color: 'violet' | 'amber' | 'rose' | 'mint';
  value: string;
  label: string;
  detail: string;
}

export function MetricCard({ icon, color, value, label, detail }: MetricCardProps) {
  return (
    <article className="metric-card">
      <span className={`metric-card__icon metric-card__icon--${color}`}>{icon}</span>
      <div>
        <span className="metric-card__label">{label}</span>
        <b className="metric-card__value">{value}</b>
        <small className="metric-card__detail">{detail}</small>
      </div>
    </article>
  );
}

import type { ReactNode } from 'react';

type Tone = 'neutral' | 'violet' | 'mint' | 'amber' | 'rose' | 'blue';

interface BadgeProps {
  children: ReactNode;
  tone?: Tone;
  dot?: boolean;
  className?: string;
}

export function Badge({ children, tone = 'neutral', dot, className = '' }: BadgeProps) {
  return (
    <span className={`badge badge--${tone} ${className}`.trim()}>
      {dot && <i className="badge__dot" aria-hidden />}
      {children}
    </span>
  );
}

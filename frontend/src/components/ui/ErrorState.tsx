import type { ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from './Button';
import { useT } from '@/i18n';

interface ErrorStateProps {
  title?: string;
  description?: string;
  icon?: ReactNode;
  onRetry?: () => void;
  retryLabel?: string;
}

export function ErrorState({
  title,
  description,
  icon,
  onRetry,
  retryLabel,
}: ErrorStateProps) {
  const t = useT();
  return (
    <div className="empty-state error-state" role="alert">
      <span className="empty-state__icon error-state__icon" aria-hidden>
        {icon ?? <AlertTriangle size={22} />}
      </span>
      <h3>{title ?? t('app.error')}</h3>
      {description && <p>{description}</p>}
      {!description && <p>{t('app.errorHint')}</p>}
      {onRetry && (
        <Button
          variant="secondary"
          size="sm"
          icon={<RefreshCw size={14} />}
          onClick={onRetry}
        >
          {retryLabel ?? t('app.retry')}
        </Button>
      )}
    </div>
  );
}

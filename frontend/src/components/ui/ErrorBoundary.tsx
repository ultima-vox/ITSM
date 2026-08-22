import { Component, type ErrorInfo, type ReactNode } from 'react';
import { ErrorState } from './ErrorState';
import { useT } from '@/i18n';

function CrashState({ onRetry }: { onRetry: () => void }) {
  const t = useT();
  return (
    <div className="error-boundary">
      <ErrorState
        title={t('app.crashTitle')}
        description={t('app.crashHint')}
        onRetry={onRetry}
      />
    </div>
  );
}

export function RouteErrorFallback() {
  return <CrashState onRetry={() => window.location.reload()} />;
}

export class ErrorBoundary extends Component<
  { children: ReactNode },
  { error: Error | null }
> {
  state: { error: Error | null } = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info.componentStack);
  }

  private reset = () => {
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      return <CrashState onRetry={this.reset} />;
    }
    return this.props.children;
  }
}

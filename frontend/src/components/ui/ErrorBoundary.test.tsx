import { createElement, type ReactElement, type ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { I18nProvider } from '@/i18n';
import { ErrorBoundary } from './ErrorBoundary';

function Healthy() {
  return createElement('span', { className: 'healthy-child' }, 'ok');
}

function Boom(): ReactNode {
  throw new Error('child-crash');
}

function wireSetState(boundary: ErrorBoundary) {
  Object.assign(boundary, {
    updater: {
      isMounted: () => true,
      enqueueSetState(
        instance: ErrorBoundary,
        payload: unknown,
        callback?: () => void,
      ) {
        const next =
          typeof payload === 'function'
            ? (payload as (state: ErrorBoundary['state']) => ErrorBoundary['state'])(
                instance.state,
              )
            : payload;
        instance.state = {
          ...instance.state,
          ...(next as ErrorBoundary['state']),
        };
        callback?.();
      },
      enqueueForceUpdate() {},
      enqueueReplaceState() {},
    },
  });
}

function markup(node: ReactNode) {
  return renderToStaticMarkup(createElement(I18nProvider, null, node));
}

describe('ErrorBoundary', () => {
  it('shows ErrorState when a child throws and recovers on retry', () => {
    const children = createElement(Healthy);
    const boundary = new ErrorBoundary({ children });
    wireSetState(boundary);

    expect(markup(boundary.render())).toContain('healthy-child');

    let thrown: Error | undefined;
    try {
      Boom();
    } catch (err) {
      thrown = err as Error;
    }
    expect(thrown).toBeInstanceOf(Error);

    boundary.state = ErrorBoundary.getDerivedStateFromError(thrown!);
    const fallback = boundary.render() as ReactElement<{ onRetry: () => void }>;
    const crashed = markup(fallback);

    expect(crashed).toContain('error-boundary');
    expect(crashed).toContain('error-state');
    expect(crashed).toContain('role="alert"');
    expect(crashed).toContain('Экран сломался');
    expect(crashed).toContain('Повторить');
    expect(crashed).not.toContain('healthy-child');

    fallback.props.onRetry();
    expect(boundary.state.error).toBeNull();
    expect(markup(boundary.render())).toContain('healthy-child');
  });
});

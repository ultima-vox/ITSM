import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Link } from 'react-router-dom';
import { useT } from '@/i18n';

export type ToastVariant = 'success' | 'info' | 'warning' | 'error';

export interface ToastAction {
  label: string;
  href: string;
}

interface ToastItem {
  id: string;
  message: string;
  variant: ToastVariant;
  action?: ToastAction;
}

interface ToastContextValue {
  toast: (message: string, variant?: ToastVariant, action?: ToastAction) => void;
  success: (message: string, action?: ToastAction) => void;
  info: (message: string, action?: ToastAction) => void;
  warning: (message: string, action?: ToastAction) => void;
  error: (message: string, action?: ToastAction) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

let toastSeq = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const t = useT();
  const [items, setItems] = useState<ToastItem[]>([]);
  const timers = useRef<Map<string, number>>(new Map());

  const dismiss = useCallback((id: string) => {
    setItems((prev) => prev.filter((item) => item.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const toast = useCallback(
    (message: string, variant: ToastVariant = 'success', action?: ToastAction) => {
      const id = `toast-${Date.now()}-${++toastSeq}`;
      setItems((prev) => [...prev.slice(-2), { id, message, variant, action }]);
      const timer = window.setTimeout(() => dismiss(id), action ? 5200 : 2800);
      timers.current.set(id, timer);
    },
    [dismiss],
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      toast,
      success: (message: string, action?: ToastAction) =>
        toast(message, 'success', action),
      info: (message: string, action?: ToastAction) => toast(message, 'info', action),
      warning: (message: string, action?: ToastAction) =>
        toast(message, 'warning', action),
      error: (message: string, action?: ToastAction) => toast(message, 'error', action),
    }),
    [toast],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-host" aria-live="polite" aria-relevant="additions">
        {items.map((item) => (
          <div
            key={item.id}
            className={`toast toast--${item.variant} toast--float`}
            role="status"
          >
            <span className="toast__msg">
              {item.message}
              {item.action && (
                <>
                  {' '}
                  <Link
                    className="toast__link"
                    to={item.action.href}
                    onClick={() => dismiss(item.id)}
                  >
                    {item.action.label}
                  </Link>
                </>
              )}
            </span>
            <button
              type="button"
              className="toast__close"
              onClick={() => dismiss(item.id)}
              aria-label={t('app.close')}
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return ctx;
}

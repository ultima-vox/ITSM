import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { useFocusTrap } from '@/hooks/useFocusTrap';
import { useT } from '@/i18n';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  labelledBy?: string;
  children: ReactNode;
  size?: 'md' | 'lg';
  className?: string;
}

export function Modal({
  open,
  onClose,
  title,
  labelledBy,
  children,
  size = 'md',
  className = '',
}: ModalProps) {
  const t = useT();
  const ref = useRef<HTMLElement>(null);
  useFocusTrap(ref, open);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <section
        ref={ref}
        className={`modal modal--${size} ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        aria-label={!labelledBy ? title : undefined}
      >
        {title && (
          <div className="modal__head">
            <h2 id={labelledBy}>{title}</h2>
            <button
              type="button"
              className="icon-btn"
              aria-label={t('app.close')}
              onClick={onClose}
            >
              <X size={18} />
            </button>
          </div>
        )}
        {children}
      </section>
    </div>
  );
}

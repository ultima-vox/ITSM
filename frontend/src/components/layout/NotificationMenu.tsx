import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, Clock3, ShieldAlert, UserPlus } from 'lucide-react';
import { useT } from '@/i18n';

interface MockNotification {
  id: string;
  kind: 'sla' | 'assign' | 'mention';
  titleKey: string;
  bodyKey: string;
  at: string;
  href: string;
  unread?: boolean;
}

const MOCK: MockNotification[] = [
  {
    id: 'n1',
    kind: 'sla',
    titleKey: 'notifications.slaRiskTitle',
    bodyKey: 'notifications.slaRiskBody',
    at: '8m',
    href: '/work-items/wi-1842',
    unread: true,
  },
  {
    id: 'n2',
    kind: 'assign',
    titleKey: 'notifications.assignTitle',
    bodyKey: 'notifications.assignBody',
    at: '22m',
    href: '/work-items/wi-1838',
    unread: true,
  },
  {
    id: 'n3',
    kind: 'mention',
    titleKey: 'notifications.mentionTitle',
    bodyKey: 'notifications.mentionBody',
    at: '1h',
    href: '/work-items/wi-1842',
  },
  {
    id: 'n4',
    kind: 'sla',
    titleKey: 'notifications.resolvedTitle',
    bodyKey: 'notifications.resolvedBody',
    at: '3h',
    href: '/work-items/wi-1820',
  },
];

const kindIcon = {
  sla: ShieldAlert,
  assign: UserPlus,
  mention: Bell,
} as const;

export function NotificationMenu() {
  const t = useT();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState(MOCK);
  const ref = useRef<HTMLDivElement>(null);
  const unread = items.filter((n) => n.unread).length;

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const markAllRead = () => {
    setItems((list) => list.map((n) => ({ ...n, unread: false })));
  };

  return (
    <div className="notif-menu" ref={ref}>
      <button
        type="button"
        className="icon-btn"
        aria-label={t('app.notifications')}
        aria-expanded={open}
        aria-haspopup="true"
        onClick={() => setOpen((v) => !v)}
      >
        <Bell size={19} />
        {unread > 0 && <span className="icon-btn__dot" aria-hidden />}
      </button>

      {open && (
        <div className="dropdown-panel notif-panel" role="menu">
          <div className="dropdown-panel__head">
            <div>
              <b>{t('app.notifications')}</b>
              {unread > 0 && (
                <span className="chip">{t('notifications.unread', { n: unread })}</span>
              )}
            </div>
            <button type="button" className="text-link" onClick={markAllRead}>
              {t('notifications.markAllRead')}
            </button>
          </div>
          <ul className="notif-list">
            {items.map((n) => {
              const Icon = kindIcon[n.kind];
              return (
                <li key={n.id}>
                  <button
                    type="button"
                    className={`notif-item${n.unread ? ' is-unread' : ''}`}
                    role="menuitem"
                    onClick={() => {
                      setItems((list) =>
                        list.map((x) => (x.id === n.id ? { ...x, unread: false } : x)),
                      );
                      setOpen(false);
                      navigate(n.href);
                    }}
                  >
                    <span className={`notif-item__icon notif-item__icon--${n.kind}`}>
                      <Icon size={14} />
                    </span>
                    <span className="notif-item__body">
                      <b>{t(n.titleKey)}</b>
                      <small>{t(n.bodyKey)}</small>
                      <em>
                        <Clock3 size={11} aria-hidden />
                        {t('notifications.ago', { n: n.at })}
                      </em>
                    </span>
                    {n.unread && <i className="notif-item__dot" aria-hidden />}
                  </button>
                </li>
              );
            })}
          </ul>
          <div className="dropdown-panel__foot">
            <button
              type="button"
              className="text-link"
              onClick={() => {
                setOpen(false);
                navigate('/my-work');
              }}
            >
              {t('notifications.viewAll')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

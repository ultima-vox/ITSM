import { useEffect, useState } from 'react';
import { AlertTriangle, Info, Megaphone, X } from 'lucide-react';
import { useT } from '@/i18n';
import { fetchActiveAnnouncements } from '@/api/announcements';
import type { Announcement } from '@/types';

const DISMISSED_KEY = 'vox.announcements.dismissed';

function readDismissed(): string[] {
  try {
    const raw = window.localStorage.getItem(DISMISSED_KEY);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

function writeDismissed(ids: string[]): void {
  try {
    window.localStorage.setItem(DISMISSED_KEY, JSON.stringify(ids));
  } catch {
    /* a full or blocked storage must not break the banner */
  }
}

function icon(severity: Announcement['severity']) {
  if (severity === 'CRITICAL') return <AlertTriangle size={16} aria-hidden />;
  if (severity === 'WARNING') return <Megaphone size={16} aria-hidden />;
  return <Info size={16} aria-hidden />;
}

/** Broadcasts addressed to the signed-in operator. A dismissal is remembered per browser. */
export function AnnouncementBanner() {
  const t = useT();
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [dismissed, setDismissed] = useState<string[]>(() => readDismissed());

  useEffect(() => {
    let cancelled = false;
    fetchActiveAnnouncements()
      .then((loaded) => {
        if (!cancelled) setAnnouncements(loaded);
      })
      .catch(() => {
        // An announcement is informational: a failed poll must never block the shell.
        if (!cancelled) setAnnouncements([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const visible = announcements.filter((entry) => !dismissed.includes(entry.id));
  if (visible.length === 0) return null;

  function dismiss(id: string) {
    const next = [...dismissed, id];
    setDismissed(next);
    writeDismissed(next);
  }

  return (
    <div className="announcement-stack">
      {visible.map((entry) => (
        <div
          key={entry.id}
          className={`announcement announcement--${entry.severity.toLowerCase()}`}
          role={entry.severity === 'CRITICAL' ? 'alert' : 'status'}
        >
          <div className="announcement__text">
            {icon(entry.severity)}
            <span>
              <b>{entry.title}</b> {entry.body}
            </span>
            {entry.linkUrl && (
              <a className="text-link" href={entry.linkUrl} rel="noreferrer noopener" target="_blank">
                {t('announcements.more')}
              </a>
            )}
          </div>
          {entry.dismissible && (
            <button
              type="button"
              className="announcement__close"
              aria-label={t('announcements.dismiss')}
              onClick={() => dismiss(entry.id)}
            >
              <X size={14} aria-hidden />
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

type TFn = (key: string, vars?: Record<string, string | number>) => string;

export function formatRelative(iso: string, t: TFn): string {
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diffMin = Math.max(0, Math.round((now - then) / 60000));
  if (diffMin < 60) return t('app.minAgo', { n: diffMin || 1 });
  const hours = Math.round(diffMin / 60);
  if (hours < 36) return t('app.hourAgo', { n: hours });
  return new Date(iso).toLocaleDateString();
}

export function formatDateTime(iso: string, locale: string): string {
  try {
    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function formatDate(iso: string, locale: string): string {
  try {
    return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(
      new Date(iso),
    );
  } catch {
    return iso;
  }
}

/** Runtime overview greeting date, e.g. "30 июля · среда" */
export function formatGreetingDate(locale: string, date: Date = new Date()): string {
  try {
    const day = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'long' }).format(
      date,
    );
    const weekday = new Intl.DateTimeFormat(locale, { weekday: 'long' }).format(date);
    return `${day} · ${weekday}`;
  } catch {
    return date.toDateString();
  }
}

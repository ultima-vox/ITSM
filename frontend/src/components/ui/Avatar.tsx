interface AvatarProps {
  initials: string;
  size?: 'sm' | 'md' | 'lg';
  tone?: 'default' | 'me';
  /** Accessible name (also sets native title tooltip). */
  title?: string;
}

export function Avatar({
  initials,
  size = 'md',
  tone = 'default',
  title,
}: AvatarProps) {
  const toneClass = tone === 'me' ? ' avatar--me' : '';
  return (
    <span
      className={`avatar avatar--${size}${toneClass}`.trim()}
      title={title}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      {initials}
    </span>
  );
}

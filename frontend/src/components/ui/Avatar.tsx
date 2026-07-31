interface AvatarProps {
  initials: string;
  size?: 'sm' | 'md' | 'lg';
  tone?: 'default' | 'me';
}

export function Avatar({ initials, size = 'md', tone = 'default' }: AvatarProps) {
  const toneClass = tone === 'me' ? ' avatar--me' : '';
  return (
    <span className={`avatar avatar--${size}${toneClass}`.trim()} aria-hidden>
      {initials}
    </span>
  );
}

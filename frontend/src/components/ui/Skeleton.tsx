interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  radius?: string | number;
  className?: string;
}

export function Skeleton({
  width = '100%',
  height = 14,
  radius = 6,
  className = '',
}: SkeletonProps) {
  return (
    <span
      className={`skeleton ${className}`.trim()}
      style={{ width, height, borderRadius: radius }}
      aria-hidden
    />
  );
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="skeleton-rows" aria-busy="true" aria-live="polite">
      {Array.from({ length: rows }).map((_, i) => (
        <div className="skeleton-row" key={i}>
          <Skeleton width="28%" height={12} />
          <Skeleton width="42%" height={12} />
          <Skeleton width="14%" height={12} />
          <Skeleton width="10%" height={12} />
        </div>
      ))}
    </div>
  );
}

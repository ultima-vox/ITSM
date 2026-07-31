import { Skeleton, SkeletonRows } from './Skeleton';

/**
 * Route-level Suspense fallback — mirrors page chrome (head + panel rows).
 */
export function PageLoader() {
  return (
    <section
      className="page page-loader"
      aria-busy="true"
      aria-live="polite"
      aria-label="Loading"
    >
      <div className="page-head page-loader__head">
        <div className="page-loader__title-block">
          <Skeleton width={180} height={28} radius={8} />
          <Skeleton width={280} height={12} radius={6} className="page-loader__subtitle" />
        </div>
        <div className="page-loader__meta">
          <Skeleton width={72} height={32} radius={999} />
          <Skeleton width={96} height={32} radius={999} />
        </div>
      </div>
      <div className="page-loader__filters">
        <Skeleton width="28%" height={36} radius={8} />
        <Skeleton width="18%" height={36} radius={8} />
        <Skeleton width="18%" height={36} radius={8} />
      </div>
      <div className="panel panel--flush page-loader__panel">
        <SkeletonRows rows={6} />
      </div>
    </section>
  );
}

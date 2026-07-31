import { useEffect } from 'react';
import { subscribeWorkItems } from '@/mock/store';

/**
 * Re-runs the given reload callbacks when the mock work-item store mutates
 * so list and detail views stay consistent without a hard navigation.
 */
export function useWorkItemsSync(...reloads: Array<() => void>): void {
  useEffect(() => {
    return subscribeWorkItems(() => {
      reloads.forEach((fn) => fn());
    });
    // reloads identity changes every render if inline — callers should pass
    // stable reload from useAsync
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, reloads);
}

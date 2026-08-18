import { useEffect } from 'react';
import { isMockMode } from '@/api/client';
import { subscribeWorkItems } from '@/mock/store';
import { subscribeNotifications } from '@/api/notifications';

/**
 * Re-runs the given reload callbacks when work items change.
 * - Mock mode: in-memory store subscription.
 * - Live mode: SSE notification stream.
 */
export function useWorkItemsSync(...reloads: Array<() => void>): void {
  useEffect(() => {
    if (isMockMode()) {
      return subscribeWorkItems(() => {
        reloads.forEach((fn) => fn());
      });
    }
    return subscribeNotifications(() => {
      reloads.forEach((fn) => fn());
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, reloads);
}

import { apiRequest, delay, isMockMode } from './client';
import { fetchDashboardMetrics, fetchWorkItems } from './workItems';

export interface WorkloadReport {
  open: number;
  resolved: number;
  unassigned: number;
  breached: number;
  atRisk: number;
  mttrHours: number | null;
  byPriority: Record<string, number>;
  byState: Record<string, number>;
  byType: Record<string, number>;
  agingBuckets: Record<string, number>;
  source: string;
  change?: {
    open: number;
    closed: number;
    rejected: number;
    successRate: number | null;
  };
  problem?: {
    open: number;
    knownErrors: number;
    resolved: number;
  };
  cmdb?: {
    configurationItems: number;
    orphans: number;
    relationships: number;
  };
  assets?: {
    total: number;
    inUse: number;
    inStock: number;
  };
  releases?: {
    inFlight: number;
    deployed: number;
    rolledBack: number;
    successRate: number | null;
  };
  effort?: {
    entries: number;
    totalMinutes: number;
    billableMinutes: number;
    itemsWithEffort: number;
  };
}

/**
 * Live: GET /reports/workload from PostgreSQL.
 * Mock: derive comparable snapshot from mock work items + dashboard metrics.
 */
export async function fetchWorkloadReport(): Promise<WorkloadReport> {
  if (isMockMode()) {
    await delay(120);
    const [metrics, items] = await Promise.all([
      fetchDashboardMetrics(),
      fetchWorkItems(),
    ]);
    const openItems = items.filter(
      (w) =>
        w.status === 'new' ||
        w.status === 'in_progress' ||
        w.status === 'waiting',
    );
    const resolved = items.filter(
      (w) => w.status === 'resolved' || w.status === 'closed',
    );
    const byPriority: Record<string, number> = {};
    const byState: Record<string, number> = {};
    const byType: Record<string, number> = {};
    for (const w of openItems) {
      byPriority[w.priority.toUpperCase()] =
        (byPriority[w.priority.toUpperCase()] ?? 0) + 1;
      byType[w.type.toUpperCase()] = (byType[w.type.toUpperCase()] ?? 0) + 1;
    }
    for (const w of items) {
      byState[w.status.toUpperCase()] =
        (byState[w.status.toUpperCase()] ?? 0) + 1;
    }
    let mttrHours: number | null = null;
    if (resolved.length > 0) {
      const totalMs = resolved.reduce((sum, w) => {
        const a = new Date(w.createdAt).getTime();
        const b = new Date(w.updatedAt).getTime();
        return sum + Math.max(0, b - a);
      }, 0);
      mttrHours = Math.round((totalMs / resolved.length / 3_600_000) * 10) / 10;
    }
    return {
      open: metrics.open,
      resolved: resolved.length,
      unassigned: openItems.filter((w) => !w.assignee).length,
      breached: metrics.breached,
      atRisk: openItems.filter((w) => w.slaState === 'at_risk').length,
      mttrHours,
      byPriority,
      byState,
      byType,
      agingBuckets: { '0_1d': 0, '1_3d': 0, '3_7d': 0, '7d_plus': 0 },
      source: 'mock-derived',
    };
  }
  return apiRequest<WorkloadReport>('/reports/workload');
}

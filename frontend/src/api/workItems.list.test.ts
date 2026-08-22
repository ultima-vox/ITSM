import { describe, expect, it } from 'vitest';
import { buildWorkItemListSearchParams } from './workItems';

describe('work item list query', () => {
  it('always sends a single page and size', () => {
    const qs = buildWorkItemListSearchParams();
    expect(qs.get('page')).toBe('0');
    expect(qs.get('size')).toBe('50');
  });

  it('passes operator filters including unassigned', () => {
    const qs = buildWorkItemListSearchParams({
      page: 1,
      size: 20,
      unassigned: true,
      teamId: 'sd-l1',
      escalated: true,
      breached: true,
      service: 'Workplace',
      state: 'in_progress',
      type: 'incident',
      priority: 'high',
    });
    expect(qs.get('page')).toBe('1');
    expect(qs.get('size')).toBe('20');
    expect(qs.get('unassigned')).toBe('true');
    expect(qs.get('teamId')).toBe('sd-l1');
    expect(qs.get('escalated')).toBe('true');
    expect(qs.get('breached')).toBe('true');
    expect(qs.get('service')).toBe('Workplace');
    expect(qs.get('state')).toBe('IN_PROGRESS');
    expect(qs.get('type')).toBe('INCIDENT');
    expect(qs.get('priority')).toBe('HIGH');
    expect(qs.get('assigneeId')).toBeNull();
  });

  it('does not walk extra pages as a query param', () => {
    const qs = buildWorkItemListSearchParams({ page: 0, size: 50 });
    expect([...qs.keys()].filter((key) => key === 'page')).toHaveLength(1);
  });
});

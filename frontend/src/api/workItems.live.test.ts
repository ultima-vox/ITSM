import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiRequest = vi.fn();

vi.mock('./client', async () => {
  const actual = await vi.importActual<typeof import('./client')>('./client');
  return {
    ...actual,
    isMockMode: () => false,
    apiRequest: (...args: unknown[]) => apiRequest(...args),
  };
});

const { bulkTransitionWorkItems, patchWorkItem } = await import('./workItems');

const unassignedDto = {
  id: '550e8400-e29b-41d4-a716-446655440000',
  number: 'INC-001',
  title: 'Test incident',
  description: 'Description',
  service: 'VPN',
  type: 'INCIDENT',
  priority: 'HIGH',
  state: 'IN_PROGRESS',
  assigneeId: null,
  requesterId: 'u-2',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-02T00:00:00Z',
};

describe('live work item writes', () => {
  beforeEach(() => {
    apiRequest.mockReset();
  });

  it('unassigns via POST assign with null assigneeId', async () => {
    apiRequest.mockResolvedValue(unassignedDto);

    const result = await patchWorkItem(unassignedDto.id, { assignee: null });

    expect(apiRequest).toHaveBeenCalledWith(`/work-items/${unassignedDto.id}/assign`, {
      method: 'POST',
      body: { assigneeId: null, teamId: undefined },
    });
    expect(result?.assignee).toBeNull();
  });

  it('bulk transition returns per-id success and fail', async () => {
    const first = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    const second = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
    apiRequest.mockResolvedValue({
      succeeded: 1,
      results: [
        { id: first, success: true, status: 'IN_PROGRESS' },
        { id: second, success: false, errorCode: 'INVALID_TRANSITION' },
      ],
    });

    const response = await bulkTransitionWorkItems([first, second], 'IN_PROGRESS');

    expect(apiRequest).toHaveBeenCalledWith('/work-items/bulk/transitions', {
      method: 'POST',
      body: {
        ids: [first, second],
        targetState: 'IN_PROGRESS',
        resolutionCode: undefined,
        resolutionNotes: undefined,
      },
    });
    expect(response.succeeded).toBe(1);
    expect(response.results[1]?.errorCode).toBe('INVALID_TRANSITION');
  });
});

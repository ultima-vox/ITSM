import { describe, expect, it } from 'vitest';
import { mapWorkItem, mapComment, mapActivity, mapStats, mapQueueStats } from './workItem';

describe('mapWorkItem', () => {
  it('maps backend enums to frontend lowercase', () => {
    const result = mapWorkItem({
      id: '550e8400-e29b-41d4-a716-446655440000',
      number: 'INC-001',
      title: 'Test incident',
      description: 'Description',
      service: 'VPN',
      type: 'INCIDENT',
      priority: 'HIGH',
      state: 'OPEN',
      assigneeId: 'u-1',
      requesterId: 'u-2',
      slaState: 'on_track',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    });
    expect(result.type).toBe('incident');
    expect(result.priority).toBe('high');
    expect(result.status).toBe('new');
    expect(result.assignee?.id).toBe('u-1');
    expect(result.requester.id).toBe('u-2');
  });

  it('handles missing optional fields gracefully', () => {
    const result = mapWorkItem({
      id: '550e8400-e29b-41d4-a716-446655440001',
      number: 'INC-002',
      title: 'Minimal',
      description: '',
      service: '',
      type: 'SERVICE_REQUEST',
      priority: 'LOW',
      state: 'RESOLVED',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    });
    expect(result.type).toBe('request');
    expect(result.priority).toBe('low');
    expect(result.assignee).toBeNull();
    expect(result.status).toBe('resolved');
    expect(result.slaState).toBe('met');
  });

  it('does not invent at_risk from priority when server omitted slaState', () => {
    const result = mapWorkItem({
      id: '550e8400-e29b-41d4-a716-446655440002',
      number: 'INC-003',
      title: 'Critical outage',
      description: '',
      service: 'VPN',
      type: 'INCIDENT',
      priority: 'CRITICAL',
      state: 'IN_PROGRESS',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    });
    expect(result.slaState).toBe('on_track');
  });

  it('keeps server slaState on list rows', () => {
    const result = mapWorkItem({
      id: '550e8400-e29b-41d4-a716-446655440003',
      number: 'INC-004',
      title: 'Breached',
      description: '',
      service: 'VPN',
      type: 'INCIDENT',
      priority: 'LOW',
      state: 'NEW',
      slaState: 'breached',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
    });
    expect(result.slaState).toBe('breached');
  });
});

describe('mapComment', () => {
  it('maps backend comment to frontend comment', () => {
    const result = mapComment({
      id: '11111111-1111-1111-1111-111111111111',
      workItemId: '550e8400-e29b-41d4-a716-446655440000',
      authorId: 'u-anna',
      body: 'This is a comment',
      internal: false,
      createdAt: '2026-01-01T12:00:00Z',
    });
    expect(result.body).toBe('This is a comment');
    expect(result.internal).toBe(false);
    expect(result.author.id).toBe('u-anna');
  });
});

describe('mapActivity', () => {
  it('maps assignment action', () => {
    const result = mapActivity({
      id: '22222222-2222-2222-2222-222222222222',
      actorId: 'u-1',
      action: 'work-item.assigned',
      occurredAt: '2026-01-01T12:00:00Z',
    });
    expect(result.kind).toBe('assignment');
  });

  it('maps transition action', () => {
    const result = mapActivity({
      id: '33333333-3333-3333-3333-333333333333',
      actorId: 'u-2',
      action: 'work-item.transitioned',
      occurredAt: '2026-01-01T12:00:00Z',
    });
    expect(result.kind).toBe('status');
  });

  it('maps comment action', () => {
    const result = mapActivity({
      id: '44444444-4444-4444-4444-444444444444',
      actorId: 'u-3',
      action: 'work-item.commented',
      occurredAt: '2026-01-01T12:00:00Z',
    });
    expect(result.kind).toBe('comment');
  });
});

describe('mapStats', () => {
  it('keeps missing CSAT as null', () => {
    expect(mapStats({ open: 3, dueToday: 1, breached: 0 }).satisfaction).toBeNull();
    expect(mapStats({ open: 3, dueToday: 1, breached: 0, csat: null }).satisfaction).toBeNull();
  });

  it('maps numeric CSAT', () => {
    expect(mapStats({ open: 3, dueToday: 1, breached: 0, csat: 84 }).satisfaction).toBe(84);
  });
});

describe('mapQueueStats', () => {
  it('maps open mine unassigned breached', () => {
    expect(
      mapQueueStats({ open: 12, mine: 4, unassigned: 3, dueToday: 1, breached: 2 }),
    ).toEqual({ open: 12, mine: 4, unassigned: 3, breached: 2 });
  });

  it('treats missing mine and unassigned as zero', () => {
    expect(mapQueueStats({ open: 5, dueToday: 0, breached: 1 })).toEqual({
      open: 5,
      mine: 0,
      unassigned: 0,
      breached: 1,
    });
  });
});

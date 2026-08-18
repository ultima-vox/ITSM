import { describe, expect, it } from 'vitest';
import { mapCabVote, mapChange } from './changes';

describe('mapChange', () => {
  it('keeps the nine backend statuses', () => {
    expect(mapChange(base({ status: 'SUBMITTED' })).status).toBe('submitted');
    expect(mapChange(base({ status: 'APPROVED' })).status).toBe('approved');
    expect(mapChange(base({ status: 'IMPLEMENTING' })).status).toBe('implementing');
    expect(mapChange(base({ status: 'REVIEW' })).status).toBe('review');
    expect(mapChange(base({ status: 'CAB_REVIEW' })).status).toBe('cab_review');
  });

  it('does not invent a schedule window', () => {
    const change = mapChange(base({ plannedStart: null, plannedEnd: null }));
    expect(change.plannedStart).toBe('');
    expect(change.plannedEnd).toBe('');
  });
});

describe('mapCabVote', () => {
  it('maps APPROVED to approve', () => {
    const vote = mapCabVote({
      approverId: 'anna',
      decision: 'APPROVED',
      decidedAt: '2026-08-18T10:00:00Z',
    });
    expect(vote.decision).toBe('approve');
    expect(vote.memberId).toBe('anna');
  });
});

function base(over: Partial<Parameters<typeof mapChange>[0]>): Parameters<typeof mapChange>[0] {
  return {
    id: 'chg-1',
    number: 'CHG-000001',
    type: 'NORMAL',
    risk: 'MEDIUM',
    status: 'DRAFT',
    title: 'Patch gateway',
    ...over,
  };
}

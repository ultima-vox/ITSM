import { describe, expect, it } from 'vitest';
import { mapProblem } from './problems';
import type { BackendProblemSummary } from './problems';

describe('mapProblem', () => {
  it('maps backend problem to frontend problem', () => {
    const dto: BackendProblemSummary = {
      id: '660e8400-e29b-41d4-a716-446655440000',
      number: 'PRB-001',
      title: 'VPN connectivity issues',
      status: 'UNDER_INVESTIGATION',
      rootCause: 'DNS misconfiguration',
      workaround: 'Use IP directly',
      resolution: null,
    };
    const result = mapProblem(dto);
    expect(result.number).toBe('PRB-001');
    expect(result.title).toBe('VPN connectivity issues');
    expect(result.status).toBe('in_progress');
    expect(result.knownError).toBe(false);
    expect(result.rootCause).toBe('DNS misconfiguration');
  });

  it('maps NEW status to new', () => {
    const dto: BackendProblemSummary = {
      id: '660e8400-e29b-41d4-a716-446655440001',
      number: 'PRB-002',
      title: 'New problem',
      status: 'NEW',
      rootCause: null,
      workaround: null,
      resolution: null,
    };
    const result = mapProblem(dto);
    expect(result.status).toBe('new');
    expect(result.knownError).toBe(false);
  });
});

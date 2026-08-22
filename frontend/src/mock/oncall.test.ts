import { describe, expect, it } from 'vitest';

import { rotationSubject } from './oncall';
import type { OnCallSchedule } from '@/types';

const START = '2026-08-03T09:00:00.000Z';

function schedule(rotationHours: number, participants: string[]): OnCallSchedule {
  return {
    id: 'sched-test',
    scheduleKey: 'test',
    name: 'Test rota',
    timeZone: 'UTC',
    rotationHours,
    rotationStart: START,
    active: true,
    participants,
  };
}

function plusDays(days: number): string {
  return new Date(new Date(START).getTime() + days * 86_400_000).toISOString();
}

describe('mock rotation matches the backend rotation maths', () => {
  it('holds the first participant until the first handover', () => {
    const weekly = schedule(168, ['alice', 'bob', 'carol']);
    expect(rotationSubject(weekly, plusDays(-3))).toBe('alice');
    expect(rotationSubject(weekly, START)).toBe('alice');
    expect(rotationSubject(weekly, plusDays(6))).toBe('alice');
  });

  it('advances once per period and wraps', () => {
    const weekly = schedule(168, ['alice', 'bob', 'carol']);
    expect(rotationSubject(weekly, plusDays(7))).toBe('bob');
    expect(rotationSubject(weekly, plusDays(14))).toBe('carol');
    expect(rotationSubject(weekly, plusDays(21))).toBe('alice');
    expect(rotationSubject(weekly, plusDays(364))).toBe('bob');
  });

  it('answers with nobody when the rotation is inactive or empty', () => {
    expect(rotationSubject({ ...schedule(24, ['alice']), active: false }, plusDays(1))).toBeNull();
    expect(rotationSubject(schedule(24, []), plusDays(1))).toBeNull();
  });
});

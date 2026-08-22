import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./client', async () => {
  const actual = await vi.importActual<typeof import('./client')>('./client');
  return {
    ...actual,
    isMockMode: () => true,
    delay: () => Promise.resolve(),
    getApiActorId: () => 'dev-local',
  };
});

const { deleteWorklog, fetchWorklogs, formatMinutes, logTime } = await import('./worklogs');

describe('formatMinutes', () => {
  it('renders hours and minutes the way an agent reads them', () => {
    expect(formatMinutes(45)).toBe('45m');
    expect(formatMinutes(60)).toBe('1h');
    expect(formatMinutes(95)).toBe('1h 35m');
    expect(formatMinutes(0)).toBe('0m');
    expect(formatMinutes(-5)).toBe('0m');
  });
});

describe('worklog mock store', () => {
  const workItemId = 'wi-worklog-test';

  beforeEach(async () => {
    const summary = await fetchWorklogs(workItemId);
    for (const entry of summary.items) {
      await deleteWorklog(workItemId, entry.id);
    }
  });

  it('rolls up the total and the billable share', async () => {
    await logTime(workItemId, {
      minutes: 45,
      startedAt: new Date(Date.now() - 3_600_000).toISOString(),
      billable: true,
    });
    await logTime(workItemId, {
      minutes: 30,
      startedAt: new Date(Date.now() - 1_800_000).toISOString(),
    });

    const summary = await fetchWorklogs(workItemId);
    expect(summary.items).toHaveLength(2);
    expect(summary.totalMinutes).toBe(75);
    expect(summary.billableMinutes).toBe(45);
    expect(new Date(summary.items[0]!.startedAt).getTime()).toBeGreaterThan(
      new Date(summary.items[1]!.startedAt).getTime(),
    );
  });

  it('refuses the entries the backend refuses', async () => {
    await expect(
      logTime(workItemId, { minutes: 0, startedAt: new Date().toISOString() }),
    ).rejects.toThrow(/minutes/);
    await expect(
      logTime(workItemId, { minutes: 2000, startedAt: new Date().toISOString() }),
    ).rejects.toThrow(/minutes/);
    await expect(
      logTime(workItemId, {
        minutes: 30,
        startedAt: new Date(Date.now() + 7_200_000).toISOString(),
      }),
    ).rejects.toThrow(/future/);
  });
});

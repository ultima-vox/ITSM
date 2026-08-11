import { describe, expect, it } from 'vitest';
import { buildTrend } from './ReportsPage';

describe('report trend source boundary', () => {
  it('never fabricates points for live empty periods', () => {
    const result = buildTrend([], false);
    expect(result.usedSynthetic).toBe(false);
    expect(result.trend).toHaveLength(7);
    expect(result.trend.every((day) => day.opened === 0 && day.closed === 0 && !day.synthetic))
      .toBe(true);
  });

  it('keeps labelled synthetic fill isolated to explicit mock mode', () => {
    const result = buildTrend([], true);
    expect(result.usedSynthetic).toBe(true);
    expect(result.trend.every((day) => day.synthetic)).toBe(true);
  });
});

import { describe, expect, it } from 'vitest';
import { shouldFetchNextWorkItemPage } from './workItems';

describe('work item list paging', () => {
  it('stops on an empty or complete page', () => {
    expect(shouldFetchNextWorkItemPage(0, 0, 0)).toBe(false);
    expect(shouldFetchNextWorkItemPage(50, 50, 50)).toBe(false);
    expect(shouldFetchNextWorkItemPage(200, 200, 500)).toBe(true);
  });

  it('caps the walk so the client cannot download the whole table', () => {
    expect(shouldFetchNextWorkItemPage(2000, 200, 8000, 2000)).toBe(false);
    expect(shouldFetchNextWorkItemPage(1800, 200, 8000, 2000)).toBe(true);
  });
});

import { describe, expect, it } from 'vitest';
import { experienceFromPath } from './experiences';

describe('experienceFromPath', () => {
  it('maps / to operator', () => {
    expect(experienceFromPath('/')).toBe('operator');
  });

  it('maps /admin to admin', () => {
    expect(experienceFromPath('/admin')).toBe('admin');
  });

  it('maps /admin/rbac to admin', () => {
    expect(experienceFromPath('/admin/rbac')).toBe('admin');
  });

  it('maps /portal to portal', () => {
    expect(experienceFromPath('/portal')).toBe('portal');
  });

  it('maps /portal/catalog to portal', () => {
    expect(experienceFromPath('/portal/catalog')).toBe('portal');
  });
});

export const EXPERIENCES = ['operator', 'portal', 'admin'] as const;

export type Experience = (typeof EXPERIENCES)[number];

export function experienceFromPath(pathname: string): Experience {
  if (pathname === '/admin' || pathname.startsWith('/admin/')) return 'admin';
  if (pathname === '/portal' || pathname.startsWith('/portal/')) return 'portal';
  return 'operator';
}

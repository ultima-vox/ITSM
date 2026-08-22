import { useEffect, useMemo, useState } from 'react';
import { useLocation, useOutletContext } from 'react-router-dom';
import { useT } from '@/i18n';
import type { CreateKind } from '@/types';
import { crumbKeys } from '@/components/layout/nav';
import type { CrumbItem } from '@/components/layout/Header';

export interface ShellOutletContext {
  openCreate: (kind: CreateKind) => void;
  openCommand: () => void;
}

export function useShell() {
  return useOutletContext<ShellOutletContext>();
}

export function useDrawerMenu() {
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [menuOpen]);

  return { menuOpen, setMenuOpen };
}

export function useCrumbs(homeKey = 'nav.overview'): CrumbItem[] {
  const t = useT();
  const location = useLocation();

  return useMemo<CrumbItem[]>(() => {
    if (location.pathname.startsWith('/work-items/')) {
      const id = location.pathname.split('/').pop() ?? '';
      return [
        { label: t('nav.queues'), to: '/queues' },
        { label: id },
      ];
    }
    const key = crumbKeys[location.pathname];
    if (!key || location.pathname === '/') {
      return [{ label: t(homeKey) }];
    }
    return [{ label: t(key) }];
  }, [location.pathname, t, homeKey]);
}

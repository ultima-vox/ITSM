import { useOutletContext } from 'react-router-dom';
import type { ShellOutletContext } from '@/components/layout/AppShell';

export function useShell() {
  return useOutletContext<ShellOutletContext>();
}

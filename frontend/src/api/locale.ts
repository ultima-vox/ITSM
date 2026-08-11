import { apiRequest, isMockMode } from './client';
import type { LocaleCode } from '@/types';

export interface BackendLocaleView {
  locale: string;
  supportedLocales: string[];
}

export async function fetchLocalePreference(): Promise<LocaleCode | null> {
  if (isMockMode()) return null;
  try {
    const view = await apiRequest<BackendLocaleView>('/me/locale');
    const code = (view.locale ?? '').slice(0, 2).toLowerCase();
    if (code === 'ru' || code === 'en' || code === 'de') return code;
    return null;
  } catch {
    return null;
  }
}

export async function updateLocalePreference(locale: LocaleCode): Promise<void> {
  if (isMockMode()) return;
  try {
    await apiRequest<void>('/me/locale', {
      method: 'PUT',
      body: { locale },
    });
  } catch {
    // Non-blocking: UI locale still updates locally
  }
}

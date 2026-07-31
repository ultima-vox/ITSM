import { RouterProvider } from 'react-router-dom';
import { AuthProvider } from '@/auth';
import { I18nProvider } from '@/i18n';
import { DensityProvider } from '@/hooks/useDensity';
import { ThemeProvider } from '@/hooks/useTheme';
import { ToastProvider } from '@/hooks/useToast';
import { router } from './router';

export function App() {
  return (
    <I18nProvider>
      <AuthProvider>
        <ThemeProvider>
          <DensityProvider>
            <ToastProvider>
              <RouterProvider router={router} />
            </ToastProvider>
          </DensityProvider>
        </ThemeProvider>
      </AuthProvider>
    </I18nProvider>
  );
}

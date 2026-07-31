import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth';
import { useT } from '@/i18n';
import { Button } from '@/components/ui';

/**
 * OIDC redirect target: exchanges ?code=…&state=… for tokens (PKCE).
 */
export function AuthCallbackPage() {
  const t = useT();
  const navigate = useNavigate();
  const { handleCallback, login, error, clearError, oidcEnabled } = useAuth();
  const [localError, setLocalError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    if (!oidcEnabled) {
      setLocalError(t('auth.notConfigured'));
      return;
    }

    void handleCallback(window.location.search).then((returnTo) => {
      if (returnTo) {
        navigate(returnTo, { replace: true });
      }
    });
  }, [handleCallback, navigate, oidcEnabled, t]);

  const displayError = localError || error;

  if (displayError) {
    return (
      <div className="auth-callback">
        <div className="auth-callback__card panel" role="alert">
          <h1>{t('auth.callbackErrorTitle')}</h1>
          <p>{displayError}</p>
          <div className="auth-callback__actions">
            <Button
              variant="primary"
              onClick={() => {
                clearError();
                setLocalError(null);
                void login('/');
              }}
            >
              {t('auth.signIn')}
            </Button>
            <Button variant="secondary" onClick={() => navigate('/', { replace: true })}>
              {t('auth.backHome')}
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-callback">
      <div className="auth-callback__card panel" aria-busy="true" aria-live="polite">
        <h1>{t('auth.callbackTitle')}</h1>
        <p>{t('auth.callbackWorking')}</p>
      </div>
    </div>
  );
}

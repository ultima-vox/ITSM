/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_USE_MOCK?: string;
  readonly VITE_API_BASE?: string;
  readonly VITE_API_TOKEN?: string;
  readonly VITE_API_ACTOR?: string;
  readonly VITE_OIDC_ENABLED?: string;
  readonly VITE_OIDC_ISSUER?: string;
  readonly VITE_OIDC_CLIENT_ID?: string;
  readonly VITE_OIDC_REDIRECT_URI?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*.json' {
  const value: Record<string, unknown>;
  export default value;
}

import { delay, isMockMode, apiRequest } from './client';

export interface IntegrationHealth {
  status: string;
  [key: string]: unknown;
}

export interface RedisIntegration {
  enabled: boolean;
  host: string;
  port: number;
  health: IntegrationHealth;
}

export interface OpenSearchIntegration {
  enabled: boolean;
  url: string;
  index: string;
  health: IntegrationHealth;
}

export interface StorageIntegration {
  type: string;
  endpoint: string;
  bucket: string;
}

export interface PlatformIntegrations {
  redis: RedisIntegration;
  opensearch: OpenSearchIntegration;
  storage: StorageIntegration;
}

const MOCK_INTEGRATIONS: PlatformIntegrations = {
  redis: {
    enabled: true,
    host: 'localhost',
    port: 6379,
    health: { status: 'UP' },
  },
  opensearch: {
    enabled: true,
    url: 'http://localhost:9200',
    index: 'itsm',
    health: { status: 'UP' },
  },
  storage: {
    type: 's3',
    endpoint: 'http://localhost:9000',
    bucket: 'itsm-attachments',
  },
};

/** GET /api/v1/platform/integrations — mock cards when VITE_USE_MOCK. */
export async function fetchPlatformIntegrations(): Promise<PlatformIntegrations> {
  if (isMockMode()) {
    await delay(180);
    return structuredClone(MOCK_INTEGRATIONS);
  }
  return apiRequest<PlatformIntegrations>('/platform/integrations');
}

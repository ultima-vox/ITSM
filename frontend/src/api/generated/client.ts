import createClient from 'openapi-fetch';

import { getApiToken } from '../client';
import type { paths } from './schema';

/** Generated-contract client for incremental replacement of handwritten adapters. */
export const generatedApi = createClient<paths>({
  headers: { Accept: 'application/json' },
});

generatedApi.use({
  async onRequest({ request }) {
    const token = getApiToken();
    if (token) request.headers.set('Authorization', `Bearer ${token}`);
    return request;
  },
});

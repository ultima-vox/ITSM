import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const oidcUrl = __ENV.OIDC_TOKEN_URL || 'http://127.0.0.1:8081/realms/itsm/protocol/openid-connect/token';
const iterations = Number(__ENV.ITERATIONS || 10);

const loginLatency = new Trend('login_latency', true);
const ticketListLatency = new Trend('ticket_list_latency', true);
const ticketCreateLatency = new Trend('ticket_create_latency', true);
const ticketTransitionLatency = new Trend('ticket_transition_latency', true);
const catalogSearchLatency = new Trend('catalog_search_latency', true);
const globalSearchLatency = new Trend('global_search_latency', true);
const cmdbTraversalLatency = new Trend('cmdb_traversal_latency', true);
const dashboardLatency = new Trend('dashboard_latency', true);
const notificationDispatchLatency = new Trend('notification_dispatch_latency', true);
const bulkImportLatency = new Trend('bulk_import_10_latency', true);
const searchIndexingLatency = new Trend('opensearch_indexing_latency', true);

const readScenario = (exec) => ({
  executor: 'shared-iterations', exec, vus: Number(__ENV.READ_VUS || 4), iterations,
  maxDuration: __ENV.MAX_DURATION || '2m',
});
const writeScenario = (exec) => ({
  executor: 'shared-iterations', exec, vus: Number(__ENV.WRITE_VUS || 2),
  iterations: Math.max(2, Math.ceil(iterations / 2)), maxDuration: __ENV.MAX_DURATION || '2m',
});

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    login: { ...readScenario('login'), vus: 1 },
    ticket_list: readScenario('ticketList'),
    ticket_create_transition: writeScenario('ticketLifecycle'),
    catalog_search: readScenario('catalogSearch'),
    global_search: readScenario('globalSearch'),
    cmdb_traversal: readScenario('cmdbTraversal'),
    dashboard: readScenario('dashboard'),
    notification_dispatch: writeScenario('notificationDispatch'),
    bulk_import: { ...writeScenario('bulkImport'), vus: 1, iterations: 2 },
    opensearch_indexing: { ...writeScenario('openSearchIndexing'), vus: 1, iterations: 2 },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    login_latency: ['p(95)<1500'],
    ticket_list_latency: ['p(95)<1000'],
    ticket_create_latency: ['p(95)<1500'],
    ticket_transition_latency: ['p(95)<1500'],
    catalog_search_latency: ['p(95)<1000'],
    global_search_latency: ['p(95)<1000'],
    cmdb_traversal_latency: ['p(95)<1500'],
    dashboard_latency: ['p(95)<1000'],
    notification_dispatch_latency: ['p(95)<1500'],
    bulk_import_10_latency: ['p(95)<10000'],
    opensearch_indexing_latency: ['p(95)<10000'],
  },
};

const jsonHeaders = { 'Content-Type': 'application/json', 'X-Actor-Id': 'load-baseline' };

function timed(metric, request) {
  const started = Date.now();
  const response = request();
  metric.add(Date.now() - started);
  return response;
}

function incident(title) {
  return JSON.stringify({
    type: 'INCIDENT', title, description: 'k6 reproducible baseline fixture', service: 'Performance',
    impact: 'MEDIUM', urgency: 'MEDIUM',
  });
}

export function setup() {
  // Exclude one-time Keycloak/JVM cold start from the steady-state login SLO.
  http.post(oidcUrl, {
    grant_type: 'password', client_id: 'itsm-backend', client_secret: 'itsm-backend-secret',
    username: 'anna', password: 'anna',
  });
  const cis = http.get(`${baseUrl}/api/v1/cmdb/cis`, { headers: jsonHeaders }).json();
  return { ciId: Array.isArray(cis) && cis.length ? cis[0].id : null };
}

export function login() {
  const response = timed(loginLatency, () => http.post(oidcUrl, {
    grant_type: 'password', client_id: 'itsm-backend', client_secret: 'itsm-backend-secret',
    username: 'anna', password: 'anna',
  }));
  check(response, { 'login token issued': (r) => r.status === 200 && !!r.json('access_token') });
}

export function ticketList() {
  const response = timed(ticketListLatency,
    () => http.get(`${baseUrl}/api/v1/work-items?page=0&size=20`, { headers: jsonHeaders }));
  check(response, { 'ticket list 200': (r) => r.status === 200 });
}

export function ticketLifecycle() {
  const title = `k6 lifecycle ${__VU}-${__ITER}-${Date.now()}`;
  const created = timed(ticketCreateLatency,
    () => http.post(`${baseUrl}/api/v1/work-items`, incident(title), { headers: jsonHeaders }));
  check(created, { 'ticket create 201': (r) => r.status === 201 });
  const id = created.json('id');
  if (!id) return;
  const assigned = http.post(`${baseUrl}/api/v1/work-items/${id}/assign`,
    JSON.stringify({ assigneeId: 'anna', teamId: 'service-desk' }), { headers: jsonHeaders });
  check(assigned, { 'ticket prerequisite assignment 200': (r) => r.status === 200 });
  if (assigned.status !== 200) return;
  const transitioned = timed(ticketTransitionLatency, () => http.post(
    `${baseUrl}/api/v1/work-items/${id}/transitions`, JSON.stringify({ targetState: 'IN_PROGRESS' }),
    { headers: jsonHeaders },
  ));
  check(transitioned, { 'ticket transition 200': (r) => r.status === 200 });
}

export function catalogSearch() {
  const response = timed(catalogSearchLatency,
    () => http.get(`${baseUrl}/api/v1/catalog/items?q=access&locale=ru`, { headers: jsonHeaders }));
  check(response, { 'catalog search 200': (r) => r.status === 200 });
}

export function globalSearch() {
  const response = timed(globalSearchLatency,
    () => http.get(`${baseUrl}/api/v1/search?q=service&limit=20`, { headers: jsonHeaders }));
  check(response, { 'global search 200': (r) => r.status === 200 });
}

export function cmdbTraversal(data) {
  if (!data.ciId) return;
  const response = timed(cmdbTraversalLatency,
    () => http.get(`${baseUrl}/api/v1/cmdb/cis/${data.ciId}/impact?hops=3`, { headers: jsonHeaders }));
  check(response, { 'CMDB traversal 200': (r) => r.status === 200 });
}

export function dashboard() {
  const response = timed(dashboardLatency,
    () => http.get(`${baseUrl}/api/v1/reports/workload`, { headers: jsonHeaders }));
  check(response, { 'dashboard report 200': (r) => r.status === 200 });
}

export function notificationDispatch() {
  const created = http.post(`${baseUrl}/api/v1/work-items`,
    incident(`k6 notification ${__VU}-${__ITER}-${Date.now()}`), { headers: jsonHeaders });
  const id = created.json('id');
  if (!id) return;
  const assigned = timed(notificationDispatchLatency, () => http.post(
    `${baseUrl}/api/v1/work-items/${id}/assign`,
    JSON.stringify({ assigneeId: 'anna', teamId: 'service-desk' }), { headers: jsonHeaders },
  ));
  check(assigned, { 'assignment notification accepted': (r) => r.status === 200 });
}

export function bulkImport() {
  const started = Date.now();
  const requests = [];
  for (let i = 0; i < 10; i += 1) {
    requests.push(['POST', `${baseUrl}/api/v1/work-items`,
      incident(`k6 bulk ${__VU}-${__ITER}-${i}-${Date.now()}`), { headers: jsonHeaders }]);
  }
  const responses = http.batch(requests);
  bulkImportLatency.add(Date.now() - started);
  check(responses, { '10-row client batch imported': (rows) => rows.every((r) => r.status === 201) });
}

export function openSearchIndexing() {
  const marker = `k6-index-${__VU}-${__ITER}-${Date.now()}`;
  const created = http.post(`${baseUrl}/api/v1/work-items`, incident(marker), { headers: jsonHeaders });
  check(created, { 'index fixture created': (r) => r.status === 201 });
  const started = Date.now();
  let found = false;
  for (let attempt = 0; attempt < 40 && !found; attempt += 1) {
    const response = http.get(`${baseUrl}/api/v1/search?q=${encodeURIComponent(marker)}&limit=10`,
      { headers: jsonHeaders });
    found = response.status === 200 && response.body.includes(marker);
    if (!found) sleep(0.25);
  }
  searchIndexingLatency.add(Date.now() - started);
  check(found, { 'OpenSearch projection visible': (value) => value === true });
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data.metrics, null, 2),
    '/results/performance-summary.json': JSON.stringify(data, null, 2),
  };
}

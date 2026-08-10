import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';

export const options = {
  scenarios: {
    operator_reads: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '30s',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
};

export default function () {
  const health = http.get(`${baseUrl}/actuator/health`);
  check(health, { 'health is 200': (response) => response.status === 200 });

  const queue = http.get(`${baseUrl}/api/v1/work-items?page=0&size=20`, {
    headers: { 'X-Actor-Id': 'load-smoke' },
  });
  check(queue, {
    'queue is 200': (response) => response.status === 200,
    'queue is JSON': (response) => response.headers['Content-Type']?.includes('application/json'),
  });
  sleep(1);
}

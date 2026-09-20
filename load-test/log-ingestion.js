import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';

const operatorUsername = __ENV.OPERATOR_USERNAME || 'operator';
const operatorPassword = __ENV.OPERATOR_PASSWORD || 'operator-local';
const authorization = `Basic ${encoding.b64encode(`${operatorUsername}:${operatorPassword}`)}`;

export const options = {
  scenarios: {
    ingestion: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const batchId = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    batchId,
    service: 'load-test-service',
    environment: 'performance',
    logs: Array.from({ length: 100 }, (_, index) => ({
      timestamp: new Date().toISOString(),
      content: `INFO request completed status=200 sequence=${index}`,
      format: 'TEXT',
      traceId: `${batchId}-${index}`,
    })),
  });
  const response = http.post('http://localhost:8080/api/v1/logs/batch', payload, {
    headers: {
      Authorization: authorization,
      'Content-Type': 'application/json',
    },
  });
  check(response, { accepted: (result) => result.status === 202 });
}

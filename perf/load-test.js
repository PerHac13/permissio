import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics Tracking
export const errorRate = new Rate('errors');
export const successRate = new Rate('success_rate');
export const authorizeDuration = new Trend('authz_duration_ms');
export const allowedCounter = new Counter('authz_allowed_total');
export const deniedCounter = new Counter('authz_denied_total');

// Configurable Scenarios (smoke, load, stress, spike)
const SCENARIO = __ENV.SCENARIO || 'smoke';

const scenarioConfigs = {
  smoke: {
    stages: [
      { duration: '3s', target: 2 },  // 2 users for 3s
      { duration: '5s', target: 5 },  // 5 users for 5s
      { duration: '2s', target: 0 },  // ramp-down
    ],
    thresholds: {
      http_req_duration: ['p(95)<150'], // SLA target
      errors: ['rate<0.01'],
    },
  },
  load: {
    stages: [
      { duration: '10s', target: 20 },
      { duration: '30s', target: 50 },
      { duration: '10s', target: 0 },
    ],
    thresholds: {
      http_req_duration: ['p(95)<150', 'p(99)<300'],
      errors: ['rate<0.01'],
    },
  },
  stress: {
    stages: [
      { duration: '10s', target: 50 },
      { duration: '30s', target: 150 },
      { duration: '20s', target: 200 },
      { duration: '10s', target: 0 },
    ],
    thresholds: {
      http_req_duration: ['p(95)<250'],
      errors: ['rate<0.05'],
    },
  },
  spike: {
    stages: [
      { duration: '5s', target: 10 },
      { duration: '10s', target: 250 }, // Instant surge
      { duration: '10s', target: 10 },
      { duration: '5s', target: 0 },
    ],
    thresholds: {
      http_req_duration: ['p(95)<350'],
    },
  },
};

export const options = scenarioConfigs[SCENARIO] || scenarioConfigs.smoke;

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'acme-dev-api-key-12345';

export function setup() {
  console.log(`Starting k6 Benchmark [Scenario: ${SCENARIO.toUpperCase()}] against ${BASE_URL}`);

  // 1. Register or Authenticate test subject
  const registerPayload = JSON.stringify({
    externalId: `perf_runner_${Date.now()}`,
    password: 'PerfPassword123!',
    attributes: { department: 'engineering', clearanceLevel: 5 },
  });

  const regRes = http.post(`${BASE_URL}/api/v1/auth/register`, registerPayload, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
    },
  });

  let token, subjectId;
  if (regRes.status === 201) {
    const regJson = regRes.json();
    token = regJson.token;
    subjectId = regJson.subjectId;
  } else {
    // Fallback: login if already registered
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
      externalId: 'alice@acme.com',
      password: 'password123',
    }), {
      headers: { 'Content-Type': 'application/json', 'X-API-Key': API_KEY },
    });
    const loginJson = loginRes.json();
    token = loginJson.token;
    subjectId = loginJson.subjectId;
  }

  // 2. Create or bind test resource
  const resPayload = JSON.stringify({
    resourceType: 'DOCUMENT',
    externalId: `perf_doc_${Date.now()}`,
    attributes: { confidentiality: 'HIGH', ownerTeam: 'engineering' },
  });

  const resRes = http.post(`${BASE_URL}/api/v1/resources`, resPayload, {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'Authorization': `Bearer ${token}`,
    },
  });

  let resourceId;
  if (resRes.status === 201) {
    resourceId = resRes.json().id;
  } else {
    resourceId = '11111111-0002-0000-0000-000000000001';
  }

  // 3. Establish OWNER relationship
  http.post(`${BASE_URL}/api/v1/relationships`, JSON.stringify({
    subjectId: subjectId,
    resourceId: resourceId,
    relation: 'OWNER',
  }), {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'Authorization': `Bearer ${token}`,
    },
  });

  return { token, subjectId, resourceId };
}

export default function (data) {
  const payload = JSON.stringify({
    subjectId: data.subjectId,
    resourceId: data.resourceId,
    action: 'READ',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'Authorization': `Bearer ${data.token}`,
    },
  };

  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/authorize`, payload, params);
  const duration = Date.now() - start;
  authorizeDuration.add(duration);

  const isSuccess = res.status === 200;
  successRate.add(isSuccess);
  errorRate.add(!isSuccess);

  if (isSuccess) {
    const body = res.json();
    if (body.allowed === true) {
      allowedCounter.add(1);
    } else {
      deniedCounter.add(1);
    }
  }

  check(res, {
    'status is 200': (r) => r.status === 200,
    'allowed is true': (r) => r.json() && r.json().allowed === true,
  });

  sleep(0.02); // 20ms pacing
}

export function handleSummary(data) {
  return {
    'k6-summary.json': JSON.stringify(data, null, 2),
  };
}

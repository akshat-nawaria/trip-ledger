// TripLedger load test (A02): throughput and p50 / p95 / p99 of the real API, database included.
//
// Two kinds of traffic at the same time, for 2 minutes:
//   devices  20 cabs, each driving trip after trip: ~1,500 pings sent in 5 uploads of ~300
//            (phones sync in batches), then reading the trip's km once at the end
//   readers  50 requests per second from operators / employees checking km and breakdowns
//
// Run it against the docker compose stack, from the TripLedger folder (no k6 install needed):
//   docker compose up -d --build
//   docker run --rm -i --network tripledger_default grafana/k6:0.54.0 run -e BASE_URL=http://app:8080 - < bench/k6/load.js
//
// Against a locally running app instead: k6 run bench/k6/load.js   (BASE_URL defaults to localhost:8080)

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TENANT = 'k6-load';
const PINGS_PER_TRIP = 1500;
const UPLOADS_PER_TRIP = 5;
const ROUTES = 50; // cabs repeat the same routes every day, so the route cache gets a fair chance
const DURATION = __ENV.DURATION || '2m';

const tripsUploaded = new Counter('trips_uploaded');
const pingsSent = new Counter('pings_sent');

export const options = {
  scenarios: {
    devices: { executor: 'constant-vus', vus: 20, duration: DURATION, exec: 'drive' },
    readers: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 20, maxVUs: 100, exec: 'read',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:upload}': ['p(99)<3000'],
    'http_req_duration{name:distance}': ['p(99)<500'],
    // also makes k6 keep separate numbers per endpoint for the summary below
    'http_req_duration{name:breakdown}': ['p(99)<1500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

// ---- setup: one token, and a few trips for the readers to look at ----------------------------------

export function setup() {
  const res = http.post(`${BASE}/v1/dev/tokens`,
    JSON.stringify({ tenant: TENANT, subject: 'k6', roles: ['ADMIN', 'OPERATOR'] }),
    { headers: { 'Content-Type': 'application/json' } });
  if (res.status !== 200) {
    throw new Error(`could not get a dev token from ${BASE} (status ${res.status}) - is the app running with DEV_TOKENS=true?`);
  }
  const token = res.json('token');
  const readable = [];
  for (let i = 0; i < 20; i++) {
    const id = `K6-SEED-${Date.now()}-${i}`;
    for (const chunk of tripChunks(i % ROUTES)) {
      upload(token, id, chunk);
    }
    readable.push(id);
  }
  return { token, readable };
}

// ---- the two kinds of traffic ------------------------------------------------------------------------

export function drive(data) {
  const route = Math.floor(Math.random() * ROUTES);
  const tripId = `K6-${__VU}-${__ITER}-${Date.now()}`;
  for (const chunk of tripChunks(route)) {
    const res = upload(data.token, tripId, chunk);
    check(res, { 'upload accepted': (r) => r.status === 202 });
    pingsSent.add(chunk.pings.length);
  }
  const km = http.get(`${BASE}/v1/trips/${tripId}/distance`, params(data.token, 'distance'));
  check(km, { 'trip has km': (r) => r.status === 200 && r.json('billableKm') !== null });
  tripsUploaded.add(1);
}

export function read(data) {
  const tripId = data.readable[Math.floor(Math.random() * data.readable.length)];
  const res = http.get(`${BASE}/v1/trips/${tripId}/distance`, params(data.token, 'distance'));
  check(res, { 'distance ok': (r) => r.status === 200 });
  if (Math.random() < 0.2) {
    const br = http.get(`${BASE}/v1/trips/${tripId}/breakdown`, params(data.token, 'breakdown'));
    check(br, { 'breakdown ok': (r) => r.status === 200 });
  }
}

// ---- helpers -----------------------------------------------------------------------------------------

function upload(token, tripId, body) {
  const p = params(token, 'upload');
  p.headers['Content-Type'] = 'application/json';
  p.headers['Idempotency-Key'] = `${tripId}-${body.pings[0].seq}`;
  return http.post(`${BASE}/v1/trips/${tripId}/evidence`, JSON.stringify(body), p);
}

function params(token, name) {
  return { headers: { Authorization: `Bearer ${token}` }, tags: { name } };
}

// One trip on route r: 1,500 pings every 4 s at ~25 km/h along a straight road, with a 160 s
// dead zone (an underpass) at 40% so every trip has one real gap to route. Same route, same
// road, so the gap snaps to the same cells and later trips find it in the cache.
function tripChunks(r) {
  const office = { lat: 12.9698, lon: 77.75 };
  const angle = (r * 360 / ROUTES) * Math.PI / 180;
  const mPerDegLat = 111195;
  const mPerDegLon = mPerDegLat * Math.cos(office.lat * Math.PI / 180);
  const start = new Date(Date.now() - 3 * 3600 * 1000);
  const at = (s) => new Date(start.getTime() + s * 1000).toISOString();
  const pings = [];
  for (let i = 0; i < PINGS_PER_TRIP; i++) {
    if (i >= 600 && i < 640) continue; // the underpass
    const metres = i * 28;
    const jitter = () => (Math.random() - 0.5) * 4 / mPerDegLat;
    pings.push({
      ts: at(i * 4),
      lat: office.lat + (metres * Math.cos(angle)) / mPerDegLat + jitter(),
      lon: office.lon + (metres * Math.sin(angle)) / mPerDegLon + jitter(),
      accuracyM: 5 + Math.floor(Math.random() * 8),
      seq: i,
    });
  }
  const base = {
    tripType: 'LOGOUT', windowStart: at(-300), windowEnd: at(3 * 3600),
    plannedOffice: office,
    milestones: [{ type: 'TRIP_START', lat: office.lat, lon: office.lon, ts: at(0) }],
    plannedStops: [],
  };
  const size = Math.ceil(pings.length / UPLOADS_PER_TRIP);
  const chunks = [];
  for (let c = 0; c < pings.length; c += size) {
    chunks.push(Object.assign({}, base, { pings: pings.slice(c, c + size) }));
  }
  return chunks;
}

// ---- summary: a markdown block that goes straight into bench/results.md ------------------------------

export function handleSummary(data) {
  const m = data.metrics;
  const ms = (v) => (v === undefined ? '-' : `${v.toFixed(1)} ms`);
  const trend = (name) => m[`http_req_duration{name:${name}}`];
  const row = (label, name) => {
    const t = trend(name);
    if (!t) return `| ${label} | - | - | - | - | - |\n`;
    const v = t.values;
    return `| ${label} | ${ms(v.med)} | ${ms(v['p(95)'])} | ${ms(v['p(99)'])} | ${ms(v.max)} | ${t.thresholds ? Object.values(t.thresholds).every((x) => x.ok) ? 'pass' : 'FAIL' : '-'} |\n`;
  };
  const seconds = data.state.testRunDurationMs / 1000;
  const reqs = m.http_reqs.values;
  const failed = m.http_req_failed.values.rate * 100;
  let md = '## k6 load test\n\n';
  md += `| | |\n|---|---|\n`;
  md += `| Duration | ${seconds.toFixed(0)} s, 20 uploading cabs + 50 reads/s |\n`;
  md += `| HTTP requests | ${reqs.count} (${reqs.rate.toFixed(1)} per second) |\n`;
  md += `| Failed requests | ${failed.toFixed(2)} % |\n`;
  md += `| Trips uploaded (1,500 pings each, 5 uploads) | ${m.trips_uploaded ? m.trips_uploaded.values.count : 0} (${m.trips_uploaded ? m.trips_uploaded.values.rate.toFixed(2) : 0} per second) |\n`;
  md += `| Pings ingested | ${m.pings_sent ? m.pings_sent.values.count : 0} (${m.pings_sent ? m.pings_sent.values.rate.toFixed(0) : 0} per second) |\n\n`;
  md += '| Endpoint | p50 | p95 | p99 | max | threshold |\n|---|---:|---:|---:|---:|---|\n';
  md += row('POST /evidence (~300 pings, km recomputed)', 'upload');
  md += row('GET /distance', 'distance');
  md += row('GET /breakdown (~1,500 segments)', 'breakdown');
  return { stdout: md + '\n' };
}

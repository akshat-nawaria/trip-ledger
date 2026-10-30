# TripLedger

**GPS gap backfill & billable distance engine.** Every kilometre, explained.

![CI](https://github.com/akshat-nawaria/trip-ledger/actions/workflows/ci.yml/badge.svg)

MoveInSync Backend Case Study 2026 · Case Study 1 · Advanced Track.

Rebuilds a trustworthy, explainable billable distance for every cab trip from unreliable GPS data.
Every kilometre carries the rule that produced it, the same input always gives the same answer, and paid routing calls are kept to a minimum.

**🎥 Demo video:** _link to be added_ — main flows, a live map-service outage, and sample API calls.

| What the case study asks | Where it is |
|---|---|
| Run it with one command | `docker compose up --build` → console at http://localhost:8080, Grafana at :3000 ([Quick start](#quick-start)) |
| The 31 km underpass trip: naive 18.4 km → **30.61 km**, 2 routing calls | `DistanceEngineTest.underpassTripComesOutAtAbout30Point6KmNot18Point4` ([examples](#the-case-study-examples-as-tests)) |
| 10,000 trips × ~1,500 pings, run time, memory, provider failing halfway | **7.4 s on one core, 89 MB heap; outage fully recovered to the exact km** ([bench/results.md](bench/results.md)) |
| Load test with throughput, p50 / p99 and the machine | **95 req/s, 0 errors, 10,091 pings/s; p99 upload 977 ms, reads < 300 ms** on an i5-13420H laptop |
| Routing calls before / after caching | every pair 15.5 M → rules 23,296 → **rules + cache 7,757 (0.78 per trip)** |
| Design note: architecture, hardest-flow sequence, failure modes | [docs/DESIGN.md](docs/DESIGN.md) |
| Assumptions · trade-offs · what was left out | [below](#assumptions-the-ones-that-matter-most) and [docs/ASSUMPTIONS.md](docs/ASSUMPTIONS.md) |
| Every requirement → where it's built → the test that proves it | [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) · **253 tests**, CI on every push |

## Quick start

### Just run it (only Docker needed)

```bash
docker compose up --build
```

The first build takes a few minutes (Maven downloads everything inside the container). Then open:

| What | Where |
|---|---|
| Demo console: upload the underpass trip, see every segment on a map, bill it, send late pings, switch the map service off and on | http://localhost:8080 |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Grafana dashboard "TripLedger" (no login needed) | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

Stop everything with `docker compose down` (add `-v` to also wipe the database).

### Develop locally

Prerequisites: JDK 21, Maven 3.9+ (bundled with IntelliJ IDEA), Docker Desktop, Git.

```bash
# 1. Start only the database (the app's tests need it too)
docker compose up -d postgres

# 2. Build, run all tests, and install the core module locally
mvn install

# 3. Run the app
mvn -pl app spring-boot:run

# 4. Check it is alive, then open the demo console or the API docs
curl http://localhost:8080/actuator/health
# browser: http://localhost:8080  and  http://localhost:8080/swagger-ui.html
```

Don't run step 3 while the full stack from `docker compose up --build` is running: both want port 8080.

### Try the API

Every `/v1` call needs a signed token. Locally, the dev token endpoint hands one out (it is switched off anywhere real):

```bash
# 1. get a token for company "acme" as operator + admin (Git Bash / macOS / Linux)
TOKEN=$(curl -s -X POST http://localhost:8080/v1/dev/tokens -H "Content-Type: application/json" \
  -d '{"tenant":"acme","subject":"demo","roles":["OPERATOR","ADMIN"]}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 2. upload a tiny trip
curl -i -X POST http://localhost:8080/v1/trips/T-DEMO-1/evidence \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: demo-1" \
  -d '{"tripType":"LOGOUT","windowStart":"2026-10-29T18:00:00+05:30","windowEnd":"2026-10-29T19:30:00+05:30",
       "plannedOffice":{"lat":12.9698,"lon":77.7500},
       "pings":[{"ts":"2026-10-29T18:00:05+05:30","lat":12.9698,"lon":77.7500,"accuracyM":5,"seq":1},
                {"ts":"2026-10-29T18:00:10+05:30","lat":12.97025,"lon":77.7500,"accuracyM":5,"seq":2}],
       "milestones":[{"type":"TRIP_START","lat":12.9698,"lon":77.7500,"ts":"2026-10-29T18:00:00+05:30"}]}'

# 3. read it back
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/trips/T-DEMO-1/distance
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/trips/T-DEMO-1/breakdown
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/trips?status=DEGRADED"
```

In Swagger UI: call `POST /v1/dev/tokens`, copy the token, click **Authorize**, paste it.

**Postman:** import [`docs/postman/TripLedger.postman_collection.json`](docs/postman/TripLedger.postman_collection.json) and run it top to bottom (or with the Collection Runner). 24 requests walk through the whole story, and each one tests its own answer: the underpass trip (9.42 km), a retry that redoes nothing, a bad GPS fix that changes nothing, billing, late pings (+0.50 km adjustment), a live map-service outage (DEGRADED → recompute → COMPUTED), and the security rules (401 / 403 / 404 / 400). No Postman? Run the same collection with Newman in Docker while the stack is up:

```bash
# macOS / Linux
docker run --rm --network tripledger_default -v "$(pwd)/docs/postman:/etc/newman" postman/newman:6-alpine \
  run TripLedger.postman_collection.json --env-var baseUrl=http://app:8080

# Windows, Git Bash (stops Git Bash from rewriting the paths)
MSYS_NO_PATHCONV=1 docker run --rm --network tripledger_default -v "$(pwd -W)/docs/postman:/etc/newman" postman/newman:6-alpine \
  run TripLedger.postman_collection.json --env-var baseUrl=http://app:8080
```

| Endpoint | Who | What it does |
|---|---|---|
| `POST /v1/trips/{tripId}/evidence` | OPERATOR, ADMIN | Upload pings, milestones, plan. Needs an `Idempotency-Key` header. Safe to retry; a trip can come in several parts |
| `GET /v1/trips/{tripId}/distance` | EMPLOYEE (own trips), OPERATOR, ADMIN | Billable km, status, rule + settings version, routing calls used |
| `GET /v1/trips/{tripId}/breakdown` | OPERATOR, ADMIN | Every segment with its rule, plus what cleaning removed and which anchors were used |
| `GET /v1/trips?status=DEGRADED` | ADMIN | Trips waiting for a better answer (routing was down) |
| `POST /v1/trips/{tripId}/recompute` | ADMIN | Compute again. Safe to repeat: unchanged input gives back the very same run |
| `POST /v1/trips/{tripId}/bill` | ADMIN | Put the current km on an invoice (`{"invoiceId": "INV-42"}`). Later changes become adjustments, never edits |
| `GET /v1/trips/{tripId}/adjustments` | OPERATOR, ADMIN | Signed corrections since billing (`+1.80`, `-0.24`) and the total charged so far |
| `GET /v1/admin/status` | ADMIN | Map service up or down, circuit breaker state, cached routes, jobs waiting / running / failed, compute lag |
| `PUT /v1/admin/routing/mock` | ADMIN, **mock provider only** | Switch the mock map service off or on (`{"down": true}`) to show an outage live |
| `GET / PUT /v1/tenant/config` | ADMIN | Your company's billing settings (no-show flag, accuracy, speed, office radius, max route). Every save is a new version |
| `POST /v1/dev/tokens` | anyone, **local only** | A token for any tenant and role, for trying things out |

Another company's trip is always **404**, never 403, so its existence is not revealed. Errors are RFC 7807 `application/problem+json` with a stable `code` and a `traceId` (also in the `X-Trace-Id` header and in every log line of that request).

### Troubleshooting

| You see | Why | Fix |
|---|---|---|
| `Connection to localhost:5432 refused` | Database not running, or Docker Desktop lost the port after sleep/restart | `docker compose down` then `docker compose up -d postgres`, wait for `(healthy)` |
| `ClassNotFoundException: com.tripledger.core...` when running only `app` | `-pl app` uses the copy of `core` saved by the last `mvn install` | `mvn install -DskipTests`, then run again |
| `Port 8080 was already in use` | An earlier run is still alive | Stop it with Ctrl + C, or find it: `netstat -ano \| findstr :8080`, then `taskkill //PID <pid> //F` |
| `401 UNAUTHORIZED` | Missing, expired or foreign token | Get a fresh one from `POST /v1/dev/tokens` |
| Grafana panels say "No data" | The app hasn't been scraped yet, or nothing happened yet | Wait 10 s, then upload a trip from the demo console |
| The map in the console stays grey | The map tiles and Leaflet come from the internet | Everything else still works offline |

## The case study examples, as tests

| Example from the case study | Test | Result |
|---|---|---|
| 31 km logout trip, 22 min without GPS, two drops inside the gap; naive sum 18.4 km | `DistanceEngineTest.underpassTripComesOutAtAbout30Point6KmNot18Point4`, `underpassNeedsOnlyTwoRoutingCalls` | **30.61 km**, 2 routing calls, every segment labelled |
| A burst of pings 9 km away for 4 s (8,000 km/h) | `TeleportFilterTest.caseStudyBurstOfNineKmInFourSecondsIsDroppedAndDistanceIsUnchanged`, `DistanceEngineTest.caseStudyTeleportBurstDoesNotChangeTheTotal` | Points dropped, distance unchanged |
| Same input + same rule version = same km | `DistanceEngineTest.shufflingTheInputNeverChangesTheResult`, `RecomputeBillingIntegrationTest.recomputingTwiceGivesTheSameRunAndTheSameKm` | Identical to the millimetre |
| Late pings after billing → signed delta, invoice untouched | `RecomputeBillingIntegrationTest.latePingsAfterBillingBecomeASignedAdjustmentAndNeverEditTheInvoice` | `+1.80` style line, invoice unchanged |
| Provider fails halfway through | `BenchmarkTest.anOutageHalfwayIsFullyRecoveredWithIdenticalKm`, `RoutingWithEngineTest`, `RecomputeBillingIntegrationTest.degradedTripsComeBackAfterAnOutageEvenWhenNothingNewIsUploaded` | DEGRADED, then recovered to the exact healthy km |
| Concurrency | `JobsIntegrationTest` (8 workers / 8 computes on one trip → one run) | No double counting |

## Assumptions (the ones that matter most)

All 50+ are in [docs/ASSUMPTIONS.md](docs/ASSUMPTIONS.md). Five of them (no-show flag, accuracy, speed limit, office radius, max route) can be changed per company with `PUT /v1/tenant/config`.

- **Bad fix:** accuracy worse than **50 m** is dropped; **teleport** = implied speed above **120 km/h**.
- **Short gap** < **70 s**; **parked** < **5 km/h**; a **move within GPS accuracy (50 m)** is never judged as speeding (rule set `v2`).
- **Rejected pieces** pay the plain straight line and are flagged for review: never over-pay on bad data.
- **Units:** integer millimetres, `StrictMath` haversine, rounded **once** at the trip total (HALF_EVEN, 10 m).
- **Logout trips** anchor the office at the start, login trips at the end. Only employees with a pickup or drop milestone get an anchor.
- **Billing** freezes a run on one invoice; later changes become signed adjustments, created only from properly COMPUTED runs.
- **Batching** of routing requests was deliberately not built: only ~2 gaps per trip need a road (A27).

## Trade-offs (short note)

Full table in [DESIGN §12](docs/DESIGN.md#12-trade-offs-and-what-was-left-out).

- **Availability over exactness when the map service is down.** Trips keep billing on a provisional straight line × 1.4 marked `DEGRADED`, and recovery restores the exact km (signed adjustment if already billed). The benchmark shows 10,000 / 10,000 trips back to identical km.
- **Strong consistency for money.** A run and its adjustment are written in one transaction; unique indexes make double counting impossible. That costs some write throughput.
- **Latency for clients, extra work for the server.** Every upload recomputes the trip straight away, so the response has the km. The load test shows the cost (upload p99 977 ms); recomputing once a trip goes quiet is the known next step.
- **Cheap and stable over perfectly precise roads.** Snapping gap ends to 22 m cells gives a 66.7 % cache hit rate and answers that never drift, at a cost of ≤ ~31 m per routed gap. Roads a bill used are pinned, so a map update never moves an old bill.
- **Simplicity over extra machinery:** the job queue lives in PostgreSQL (`SKIP LOCKED`), not Kafka; tenant isolation is in the app, not also in row-level security.

**Left out on purpose:** request batching · Redis second-level cache · row-level security · rate limiter for a real provider · alert rules · HMM map-matching · live distance during the trip · GPS-spoofing detection · tolls and waiting charges · production SSO.

### Caching: what can be stale

| Data | Stale for at most | Bounded by |
|---|---|---|
| Road distance between two cells (Caffeine, max 100,000, W-TinyLFU) | 7 days after a map change | TTL 7 d; failures never cached; restart empties it |
| Roads a billed run used (`run_route`) | Never refreshed, on purpose | A bill must not move because a map changed |
| A `DEGRADED` (× 1.4) answer | Breaker wait 30 s + one scan ≤ 60 s after the provider is back | Labelled `DEGRADED`; signed adjustment if billed meanwhile |
| A trip's km right after an upload | Normally 0 (computed inline), at most one worker poll (2 s) | The API says `RECEIVED` instead of showing old km |
| Tenant settings | Never (read on every computation) | Each run records the settings version used |

Details: [DESIGN §7](docs/DESIGN.md#7-routing-cost-and-caching).

## Project layout

```
core/   plain Java: the distance engine (no Spring, no database)
app/    Spring Boot: REST API, JWT security + tenants, PostgreSQL (Flyway + Spring JDBC), routing layer, job queue + billing
monitoring/  Prometheus scrape config, Grafana data source + "TripLedger" dashboard (provisioned, nothing to click)
bench/  10,000-trip engine benchmark with a mid-run outage (plain Java, uses core + app as libraries) and the k6 load test script
docs/   requirements tracker, assumptions, stack choice, roadmap, design note, Postman demo collection
Dockerfile   two-stage build: Maven + JDK 21 builds the jar, a slim JRE 21 image runs it as a non-root user
```

## What works so far

| Area | Classes | Proven by |
|---|---|---|
| Benchmark + load test | `TripFactory` (realistic synthetic trips, fixed seed), `Benchmark` (two runs + recovery, report), `bench/k6/load.js` | `TripFactoryTest`, `BenchmarkTest` (400-trip outage run in CI: everything recovered, identical km); results in [bench/results.md](bench/results.md) |
| Monitoring + demo | `TripLedgerMetrics` (counters + compute timer), `GaugesConfig` (breaker, cache, queue depth, compute lag), `AdminController`, ECS JSON logs in Docker, `static/index.html` console, Grafana dashboard (15 panels) | `MetricsIntegrationTest` (scrape has our metrics, no tenant or trip ids in them, console served), `AdminIntegrationTest` (status, live outage switch → DEGRADED, operators get 403) |
| Exact distances | `GeoPoint` (micro-degrees), `Distance` (integer mm), `Haversine` (StrictMath) | `GeoPointTest`, `DistanceTest`, `HaversineTest` |
| One place for every threshold | `BillingRules` (case-study defaults + builder) | `BillingRulesTest` |
| Anchor points | `EmployeeAnchorResolver` (actual → profile home → planned), `NoShowAnchorResolver` (behind a flag), `OfficeAnchorResolver` (400 m rule) | One test class per resolver |
| Segment rules 0–6 | `ImpossibleSpeedRule`, `ShortGapRule`, `ParkedRule`, `RoutingUnavailableRule`, `RouteTooLongRule`, `RouteTooFastRule`, `RoutedRule` in `RuleSet.v2()` | `RuleSetTest` (every boundary: 69/70 s, 4.9/5.1 km/h, 100 km) |
| The engine | `DistanceEngine.compute(evidence, routeLookup)` → `TripDistance` with a label on every segment | `DistanceEngineTest` (underpass 18.4 → 30.61 km, outage → DEGRADED, shuffled input → identical result) |
| Jobs, recovery, billing | Compute queue in Postgres (`JobRepository`, `ComputeWorker`, lease + `SKIP LOCKED`), `RecoveryScanner`, `PinnedRoutes`, `ResultWriter` (run + signed adjustment in one transaction), `BillingService` | `JobsIntegrationTest` (crash, failure, 2 concurrency tests), `RecomputeBillingIntegrationTest` (R10, R11, R12, pinned routes), `PinnedRoutesTest`, `JobSettingsTest` |
| Security + tenancy | JWT (`SecurityConfig`, `TenantClaimValidator`, `CurrentCaller`), roles per endpoint, employee own-trip rule, per-tenant `TenantSettings` (Flyway `V2`), `Problems` + `ApiExceptionHandler` + `TraceIdFilter` | `SecurityIntegrationTest` (12), `TenantConfigIntegrationTest` (5), `ErrorHandlingIntegrationTest` (9, incl. a log scan), `CallerTest`, `TenantSettingsTest` |
| REST API + database | `TripController` → `EvidenceService` (idempotent, split uploads) → `DistanceService` (fingerprint, reuse or compute, save run) → JDBC repositories; Flyway `V1` schema; Swagger UI | `TripApiIntegrationTest` (15 tests on a real PostgreSQL), `EvidenceFingerprintTest` |
| Routing layer | `RoutingGateway` (snap → `RouteCache` → `CircuitBreakingProvider` → `MockRoutingProvider`), per-trip `RoutingUsage`, settings in `application.yml` | `RouteKeyTest`, `RouteCacheTest`, `CircuitBreakingProviderTest`, `RoutingGatewayTest`, `RoutingWithEngineTest` (outage → DEGRADED, recovery → normal, 20 trips → 1 call) |
| GPS cleaning | `InvalidCoordinateFilter` → `DuplicateFilter` → `TimeOrderFilter` → `AccuracyFilter` → `TeleportFilter`, run by `CleaningPipeline` | One test class per filter + `CleaningPipelineTest` (shuffled 1,000× → same result) |

Run only the engine tests (fast, no database needed):

```bash
mvn -pl core test
```

See the routing setup of a running app (provider, breaker state, cached routes):

```bash
curl http://localhost:8080/actuator/info
```

## Benchmark and load test

Full numbers, machine and how to repeat them: **[bench/results.md](bench/results.md)**. Laptop: Intel Core i5-13420H (12 threads), 15.6 GB RAM, Windows 11.

| | |
|---|---|
| 10,000 trips, 15.7 M pings, one thread | **7.4 s** engine time, p50 / p99 per trip **0.62 / 2.43 ms**, peak heap **89 MB** |
| Same trips, 12 threads | **1.9 s** (5,129 trips/s, 8.1 M pings/s) |
| Paid routing calls (R15) | every pair of points 15.5 M → rules only 23,296 → **rules + cache 7,757 (0.78 per trip)** |
| Map service killed halfway | 40 calls hit the dead service before the breaker opened; 583 trips stayed COMPUTED from the cache; after recovery **10,000 / 10,000 trips have exactly the healthy-run km** |
| API under load (k6, 2 min, full Docker stack) | 20 cabs uploading + 50 reads/s: **95 req/s, 0 errors**, **10,091 pings/s** ingested; p99 upload **977 ms**, `/distance` **246 ms**, `/breakdown` **298 ms** |

```bash
mvn -q install -DskipTests && mvn -q -pl bench exec:exec      # engine benchmark, ~1 min

# load test, ~3 min (stack running: docker compose up -d --build)
docker run --rm -i --network tripledger_default grafana/k6:0.54.0 run -e BASE_URL=http://app:8080 - < bench/k6/load.js
```

## Documents

- [Design note](docs/DESIGN.md) — architecture, data model, APIs, rule order, failure modes, trade-offs
- [Requirements tracker](docs/REQUIREMENTS.md) — every PDF item, where it is built, how it is proved
- [Assumptions](docs/ASSUMPTIONS.md) — decisions where the PDF is open
- [Technology choice](docs/STACK.md) — why Java 21 + Spring Boot
- [Benchmark and load test results](bench/results.md) — numbers, machine, the two bugs they found
- [Postman collection](docs/postman/TripLedger.postman_collection.json) — the whole demo as 24 self-checking requests
- [Roadmap](docs/ROADMAP.md) — the 11 build segments

### Metrics

Scraped from `/actuator/prometheus` (open, like `/actuator/health`; it holds counts only, never tenant or trip ids).

| Metric | Meaning |
|---|---|
| `tripledger_trips_computed_total{status}` | Results saved, COMPUTED or DEGRADED |
| `tripledger_trips_reused_total` | Computations skipped because nothing changed |
| `tripledger_compute_duration_seconds` | Time per computation, routing included (p95 on the dashboard) |
| `tripledger_segments_total{rule}` | Pieces measured by each rule; `REJECTED_*` = rejections |
| `tripledger_routing_lookups_total{source}` | Road distances from `cache`, `provider` (paid) or `replayed` (pinned) |
| `tripledger_routing_unavailable_total` | Gaps that fell back to straight line × 1.4 |
| `tripledger_routing_breaker_state` | 0 closed, 1 half-open, 2 open |
| `tripledger_jobs_queue{status}` | Jobs `pending`, `running`, `failed_24h` (queue depth) |
| `tripledger_jobs_oldest_pending_age_seconds` | Compute lag |
| `tripledger_jobs_finished_total{outcome}` | `done`, `retry`, `failed` |
| `tripledger_ingest_uploads_total{result}`, `tripledger_ingest_pings_total{result}` | Uploads accepted / replayed; pings new / duplicate |
| `tripledger_adjustments_created_total` | Signed corrections to billed trips |
| `http_server_requests_seconds` | API rate, latency and error rate (Spring's own) |

Inside Docker the logs are JSON (Elastic Common Schema), one line per event, with `traceId`, `tenantId` and `tripId` fields.

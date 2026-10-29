# TripLedger

**GPS gap backfill & billable distance engine.** Every kilometre, explained.

![CI](https://github.com/akshat-nawaria/trip-ledger/actions/workflows/ci.yml/badge.svg)

MoveInSync Backend Case Study 2026 · Case Study 1 · Advanced Track.

Rebuilds a trustworthy, explainable billable distance for every cab trip from unreliable GPS data.
Every kilometre carries the rule that produced it, the same input always gives the same answer, and paid routing calls are kept to a minimum.

> Status: **Segments S0–S8 complete.** Distance engine (underpass trip **18.4 → 30.61 km**, **2 routing calls**), cached + circuit-broken routing (20 trips over one gap = **1** paid call), secured multi-company REST API on PostgreSQL, a crash-safe job queue with automatic recompute and **signed adjustments** for late pings, and now Prometheus metrics, a Grafana dashboard and a demo console, all started by **one `docker compose up`**. **242 tests.** See [docs/ROADMAP.md](docs/ROADMAP.md).

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

## Project layout

```
core/   plain Java: the distance engine (no Spring, no database)
app/    Spring Boot: REST API, JWT security + tenants, PostgreSQL (Flyway + Spring JDBC), routing layer, job queue + billing
monitoring/  Prometheus scrape config, Grafana data source + "TripLedger" dashboard (provisioned, nothing to click)
docs/   requirements tracker, assumptions, stack choice, roadmap, design note
Dockerfile   two-stage build: Maven + JDK 21 builds the jar, a slim JRE 21 image runs it as a non-root user
```

## What works so far

| Area | Classes | Proven by |
|---|---|---|
| Monitoring + demo | `TripLedgerMetrics` (counters + compute timer), `GaugesConfig` (breaker, cache, queue depth, compute lag), `AdminController`, ECS JSON logs in Docker, `static/index.html` console, Grafana dashboard (15 panels) | `MetricsIntegrationTest` (scrape has our metrics, no tenant or trip ids in them, console served), `AdminIntegrationTest` (status, live outage switch → DEGRADED, operators get 403) |
| Exact distances | `GeoPoint` (micro-degrees), `Distance` (integer mm), `Haversine` (StrictMath) | `GeoPointTest`, `DistanceTest`, `HaversineTest` |
| One place for every threshold | `BillingRules` (case-study defaults + builder) | `BillingRulesTest` |
| Anchor points | `EmployeeAnchorResolver` (actual → profile home → planned), `NoShowAnchorResolver` (behind a flag), `OfficeAnchorResolver` (400 m rule) | One test class per resolver |
| Segment rules 0–6 | `ImpossibleSpeedRule`, `ShortGapRule`, `ParkedRule`, `RoutingUnavailableRule`, `RouteTooLongRule`, `RouteTooFastRule`, `RoutedRule` in `RuleSet.v1()` | `RuleSetTest` (every boundary: 69/70 s, 4.9/5.1 km/h, 100 km) |
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

## Documents

- [Design note](docs/DESIGN.md) — architecture, data model, APIs, rule order, failure modes, trade-offs
- [Requirements tracker](docs/REQUIREMENTS.md) — every PDF item, where it is built, how it is proved
- [Assumptions](docs/ASSUMPTIONS.md) — decisions where the PDF is open
- [Technology choice](docs/STACK.md) — why Java 21 + Spring Boot
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

## Sections still to come

- API reference and sample calls (S10)
- Benchmark results and machine (S9)
- Trade-offs and what was left out (S10)
- Demo video (S10)

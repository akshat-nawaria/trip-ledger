# Requirements Tracker

Every gradable item from the case study PDF, the segment that builds it, and the test that proves it.
Tick the box when the proving test is green.

Legend: **S0–S10** = build segments (see `docs/ROADMAP.md`).

## Core case study (Sections I–IV + key points)

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| R01 | Ingest pings (lat, lon, timestamp, accuracy), milestones, planned stops, office | S5 | Evidence API integration test | [x] `POST /v1/trips/{id}/evidence`, idempotent, multi-part uploads (`TripApiIntegrationTest`) |
| R02 | Remove duplicates, sort by time, drop low-accuracy fixes, drop teleports (> 120 km/h, configurable) | S2 | One unit test per filter; 9 km in 4 s burst dropped | [x] |
| R03 | Employee anchor: actual point → ad-hoc profile home + sign-in time → planned location + planned time | S3 | Three fallback-branch tests | [x] |
| R04 | No-show anchor behind a config flag (no-show location, else planned) | S3 | Flag on / off tests | [x] Flag per company via `PUT /v1/tenant/config`, versioned (S6) |
| R05 | Office anchor: actual trip end only if within 400 m of planned office, else planned office + trip-end time | S3 | 399 m vs 401 m test | [x] |
| R06 | Short gap (< 70 s, plausible speed) and parked (< 5 km/h) use straight-line distance | S3 | 69 s / 70 s and 4.9 / 5.1 km/h tests | [x] |
| R07 | Real gaps use routed driving distance; reject if > 100 km or speed rule fails | S3 | 100 km / 100.1 km and speed tests | [x] |
| R08 | Every segment carries the rule that produced it (SHORT_GAP_AERIAL, PARKED, ROUTED, REJECTED_SPEED…) | S3 | Breakdown endpoint shows labels | [x] |
| R09 | Cache routing by snapped cells, batch requests, report calls per trip | S4 | Calls before / after cache | [x] Snapped-cell cache + per-trip `RoutingUsage` (S4). Batching deliberately dropped, see A27 |
| R10 | Provider down: open circuit, haversine × 1.4, mark trip DEGRADED, auto-recompute on recovery | S4, S7 | Provider killed mid-run test | [x] Breaker + cache during outage (S4); DEGRADED re-queued every minute once the breaker is CLOSED, worker recomputes; billed-while-degraded trips settled by adjustment (S7, `RecomputeBillingIntegrationTest`) |
| R11 | Idempotent recompute: same input + same rule version = same km | S7 | Run twice → identical | [x] Fingerprint reuse + unique index (S5); `POST /recompute` twice → same run, same km; pinned routes replayed (S7) |
| R12 | Late pings after billing produce a signed delta, never edit a closed invoice | S7 | Late-ping test | [x] `POST /bill` freezes run + km; late pings → signed `invoice_adjustment`, invoice never edited; re-send adds nothing (S7) |
| R13 | Deterministic, versioned rule order | S3 | Golden-file test | [x] |
| R14 | State units and rounding (how and when) | S2, S3 | Order-independence test | [x] |
| R15 | Show provider-call count before and after caching | S4, S9 | README table | [~] Counted per trip (S4) and stored on every run, shown by `GET /distance` (S5); test: 20 trips over one gap = 20 lookups, 1 provider call. Full table in S9 |
| R16 | Benchmark 10,000 trips × ~1,500 pings: run time + memory, provider failure halfway | S9 | `bench/results.md` | [ ] |

## Plus points

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| P01 | Authentication, roles (employee / operator / admin), tenant isolation | S6 | Cross-tenant read returns 404 | [x] JWT + roles per endpoint; employees see only own trips; other tenant = 404 (`SecurityIntegrationTest`) |
| P02 | Time and space complexity, small benchmark, where it stops scaling | S1, S9 | DESIGN.md + benchmark | [ ] |
| P03 | Fault tolerance: crashes, timeouts, duplicates, out-of-order, partial failure; one failure-injection test | S5, S7 | Crash + duplicate tests | [x] Duplicates, retries, split uploads, outage (S5); crashed-worker lease recovery, failing job → FAILED, retry back-off (S7, `JobsIntegrationTest`) |
| P04 | OOP: encapsulation, inheritance, polymorphism; controller / service / repository | S2–S5 | Core runs with no Spring | [x] `TripController` → `EvidenceService` / `DistanceService` / `TripQueryService` → repositories; `CoreModuleIsolationTest` |
| P05 | Documented trade-offs (consistency vs availability vs latency), what was left out | S1, S10 | README section | [ ] |
| P06 | Metrics + structured logging: lag, error rate, rejections, retries, queue depth | S8 | Grafana dashboard | [x] Prometheus metrics for lag, queue depth, retries, rejections (`segments{rule=REJECTED_*}`), routing calls, breaker; API error rate + p95 from Spring; provisioned Grafana dashboard; ECS JSON logs with `traceId` / `tenantId` / `tripId` (`MetricsIntegrationTest`) |
| P07 | Caching with eviction policy; what can go stale, for how long, how it is bounded | S4 | Staleness table | [ ] |
| P08 | Error handling with non-leaking messages; no personal data in logs or errors | S6 | Log-scan test | [x] problem+json with `code` + `traceId`; 500s say nothing internal; log scan finds no employee refs or coordinates (`ErrorHandlingIntegrationTest`) |

## Advanced track

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| A01 | Every hard rule has an automated test, incl. one concurrency test and one failure test | S2–S7 | CI green | [x] Rule/filter/anchor unit tests (S2–S3); concurrency: 8 workers / 8 computes on one trip (S7); failure: outage, crash, missing trip (S5–S7) |
| A02 | Load test with throughput and p50 / p99, machine stated | S9 | k6 results in README | [ ] |
| A03 | Design note: architecture diagram, sequence diagram, failure-mode table | S1 | `docs/DESIGN.md` | [x] |
| A04 | One-command run (`docker compose up`) incl. database | S0, S8 | Fresh-clone test | [x] `docker compose up --build` starts PostgreSQL, the app (two-stage Dockerfile), Prometheus and Grafana; demo console at `:8080` |

## Submission instructions

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| I01 | Public GitHub repo with meaningful commit history | S0, S10 | Commit log | [ ] |
| I02 | Demo video: main flows, one live failure, sample API calls | S10 | Link in README | [ ] Demo console + live map-service switch ready (S8) |

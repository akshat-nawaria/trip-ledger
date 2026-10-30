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
| R10 | Provider down: open circuit, haversine × 1.4, mark trip DEGRADED, auto-recompute on recovery | S4, S7 | Provider killed mid-run test | [x] Breaker + cache during outage (S4); DEGRADED re-queued every minute once the breaker is CLOSED, worker recomputes; billed-while-degraded trips settled by adjustment (S7, `RecomputeBillingIntegrationTest`); recovery also works with no new traffic, via trial trips while HALF_OPEN (S9); 10,000-trip outage run fully recovered (`BenchmarkTest`) |
| R11 | Idempotent recompute: same input + same rule version = same km | S7 | Run twice → identical | [x] Fingerprint reuse + unique index (S5); `POST /recompute` twice → same run, same km; pinned routes replayed (S7) |
| R12 | Late pings after billing produce a signed delta, never edit a closed invoice | S7 | Late-ping test | [x] `POST /bill` freezes run + km; late pings → signed `invoice_adjustment`, invoice never edited; re-send adds nothing (S7) |
| R13 | Deterministic, versioned rule order | S3 | Golden-file test | [x] |
| R14 | State units and rounding (how and when) | S2, S3 | Order-independence test | [x] |
| R15 | Show provider-call count before and after caching | S4, S9 | README table | [x] Counted per trip (S4), stored on every run, shown by `GET /distance` (S5). 10,000-trip table: every pair 15.5 M → rules only 23,296 → rules + cache **7,757 (0.78/trip)** (`bench/results.md`) |
| R16 | Benchmark 10,000 trips × ~1,500 pings: run time + memory, provider failure halfway | S9 | `bench/results.md` | [x] 15.7 M pings: 7.4 s on one core (p99 2.43 ms/trip), 1.9 s on 12 threads, 89 MB peak heap; outage halfway → breaker opens after 40 calls, every trip recovered to identical km (`Benchmark`, `BenchmarkTest` in CI) |

## Plus points

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| P01 | Authentication, roles (employee / operator / admin), tenant isolation | S6 | Cross-tenant read returns 404 | [x] JWT + roles per endpoint; employees see only own trips; other tenant = 404 (`SecurityIntegrationTest`) |
| P02 | Time and space complexity, small benchmark, where it stops scaling | S1, S9 | DESIGN.md + benchmark | [x] DESIGN §11 + measured limits in `bench/results.md` |
| P03 | Fault tolerance: crashes, timeouts, duplicates, out-of-order, partial failure; one failure-injection test | S5, S7 | Crash + duplicate tests | [x] Duplicates, retries, split uploads, outage (S5); crashed-worker lease recovery, failing job → FAILED, retry back-off (S7, `JobsIntegrationTest`) |
| P04 | OOP: encapsulation, inheritance, polymorphism; controller / service / repository | S2–S5 | Core runs with no Spring | [x] `TripController` → `EvidenceService` / `DistanceService` / `TripQueryService` → repositories; `CoreModuleIsolationTest` |
| P05 | Documented trade-offs (consistency vs availability vs latency), what was left out | S1, S10 | README section | [x] README "Trade-offs (short note)" + "Left out on purpose"; full table in DESIGN §12 |
| P06 | Metrics + structured logging: lag, error rate, rejections, retries, queue depth | S8 | Grafana dashboard | [x] Prometheus metrics for lag, queue depth, retries, rejections (`segments{rule=REJECTED_*}`), routing calls, breaker; API error rate + p95 from Spring; provisioned Grafana dashboard; ECS JSON logs with `traceId` / `tenantId` / `tripId` (`MetricsIntegrationTest`) |
| P07 | Caching with eviction policy; what can go stale, for how long, how it is bounded | S4, S10 | Staleness table | [x] Caffeine W-TinyLFU, 100k roads, 7-day TTL, failures never cached, pinned roads per run; staleness table in README and DESIGN §7 |
| P08 | Error handling with non-leaking messages; no personal data in logs or errors | S6 | Log-scan test | [x] problem+json with `code` + `traceId`; 500s say nothing internal; log scan finds no employee refs or coordinates (`ErrorHandlingIntegrationTest`) |

## Advanced track

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| A01 | Every hard rule has an automated test, incl. one concurrency test and one failure test | S2–S7 | CI green | [x] Rule/filter/anchor unit tests (S2–S3); concurrency: 8 workers / 8 computes on one trip (S7); failure: outage, crash, missing trip (S5–S7) |
| A02 | Load test with throughput and p50 / p99, machine stated | S9 | k6 results in README | [x] k6, 2 min on the Docker stack, i5-13420H laptop: 95 req/s, 0 errors, 10,091 pings/s; p50/p99 upload 478/977 ms, distance 58/246 ms (`bench/results.md`) |
| A03 | Design note: architecture diagram, sequence diagram, failure-mode table | S1 | `docs/DESIGN.md` | [x] |
| A04 | One-command run (`docker compose up`) incl. database | S0, S8 | Fresh-clone test | [x] `docker compose up --build` starts PostgreSQL, the app (two-stage Dockerfile), Prometheus and Grafana; demo console at `:8080` |

## Submission instructions

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| I01 | Public GitHub repo with meaningful commit history | S0, S10 | Commit log | [x] One commit per logical change, segment by segment (40+ commits), CI on every push |

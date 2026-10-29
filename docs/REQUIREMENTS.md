# Requirements Tracker

Every gradable item from the case study PDF, the segment that builds it, and the test that proves it.
Tick the box when the proving test is green.

Legend: **S0–S10** = build segments (see `docs/ROADMAP.md`).

## Core case study (Sections I–IV + key points)

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| R01 | Ingest pings (lat, lon, timestamp, accuracy), milestones, planned stops, office | S5 | Evidence API integration test | [ ] |
| R02 | Remove duplicates, sort by time, drop low-accuracy fixes, drop teleports (> 120 km/h, configurable) | S2 | One unit test per filter; 9 km in 4 s burst dropped | [x] |
| R03 | Employee anchor: actual point → ad-hoc profile home + sign-in time → planned location + planned time | S3 | Three fallback-branch tests | [ ] |
| R04 | No-show anchor behind a config flag (no-show location, else planned) | S3 | Flag on / off tests | [ ] |
| R05 | Office anchor: actual trip end only if within 400 m of planned office, else planned office + trip-end time | S3 | 399 m vs 401 m test | [ ] |
| R06 | Short gap (< 70 s, plausible speed) and parked (< 5 km/h) use straight-line distance | S3 | 69 s / 70 s and 4.9 / 5.1 km/h tests | [ ] |
| R07 | Real gaps use routed driving distance; reject if > 100 km or speed rule fails | S3 | 100 km / 100.1 km and speed tests | [ ] |
| R08 | Every segment carries the rule that produced it (SHORT_GAP_AERIAL, PARKED, ROUTED, REJECTED_SPEED…) | S3 | Breakdown endpoint shows labels | [ ] |
| R09 | Cache routing by snapped cells, batch requests, report calls per trip | S4 | Calls before / after cache | [ ] |
| R10 | Provider down: open circuit, haversine × 1.4, mark trip DEGRADED, auto-recompute on recovery | S4, S7 | Provider killed mid-run test | [ ] |
| R11 | Idempotent recompute: same input + same rule version = same km | S7 | Run twice → identical | [ ] |
| R12 | Late pings after billing produce a signed delta, never edit a closed invoice | S7 | Late-ping test | [ ] |
| R13 | Deterministic, versioned rule order | S3 | Golden-file test | [ ] |
| R14 | State units and rounding (how and when) | S2, S3 | Order-independence test | [x] |
| R15 | Show provider-call count before and after caching | S4, S9 | README table | [ ] |
| R16 | Benchmark 10,000 trips × ~1,500 pings: run time + memory, provider failure halfway | S9 | `bench/results.md` | [ ] |

## Plus points

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| P01 | Authentication, roles (employee / operator / admin), tenant isolation | S6 | Cross-tenant read returns 404 | [ ] |
| P02 | Time and space complexity, small benchmark, where it stops scaling | S1, S9 | DESIGN.md + benchmark | [ ] |
| P03 | Fault tolerance: crashes, timeouts, duplicates, out-of-order, partial failure; one failure-injection test | S5, S7 | Crash + duplicate tests | [ ] |
| P04 | OOP: encapsulation, inheritance, polymorphism; controller / service / repository | S2–S5 | Core runs with no Spring | [ ] |
| P05 | Documented trade-offs (consistency vs availability vs latency), what was left out | S1, S10 | README section | [ ] |
| P06 | Metrics + structured logging: lag, error rate, rejections, retries, queue depth | S8 | Grafana dashboard | [ ] |
| P07 | Caching with eviction policy; what can go stale, for how long, how it is bounded | S4 | Staleness table | [ ] |
| P08 | Error handling with non-leaking messages; no personal data in logs or errors | S6 | Log-scan test | [ ] |

## Advanced track

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| A01 | Every hard rule has an automated test, incl. one concurrency test and one failure test | S2–S7 | CI green | [ ] |
| A02 | Load test with throughput and p50 / p99, machine stated | S9 | k6 results in README | [ ] |
| A03 | Design note: architecture diagram, sequence diagram, failure-mode table | S1 | `docs/DESIGN.md` | [x] |
| A04 | One-command run (`docker compose up`) incl. database | S0, S8 | Fresh-clone test | [~] database done (S0); app added in S8 |

## Submission instructions

| ID | Requirement | Segment | Proof | Done |
|----|-------------|---------|-------|------|
| I01 | Public GitHub repo with meaningful commit history | S0, S10 | Commit log | [ ] |
| I02 | Demo video: main flows, one live failure, sample API calls | S10 | Link in README | [ ] |

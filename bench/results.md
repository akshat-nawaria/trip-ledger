# Benchmark and load test results

Measured on 2026-10-30 on a developer laptop, not a server. Both runs can be repeated with the commands at the bottom.

## Machine

| | |
|---|---|
| Laptop | 13th Gen Intel Core i5-13420H (8 cores: 4 performance + 4 efficient, 12 threads), 15.6 GB RAM |
| OS | Windows 11, plugged in |
| Java | OpenJDK 21.0.12, G1 garbage collector, heap limit `-Xmx1g` |
| Load test | App, PostgreSQL 16 and k6 all in Docker Desktop on the same laptop |

---

## Part 1: engine benchmark (R16)

`mvn -q -pl bench exec:exec`: the real `DistanceEngine` and the app's real routing chain (snapped-cell cache → circuit breaker → mock map service). There's no HTTP and no database here; that's what the load test in part 2 covers.

### Workload

| | |
|---|---|
| Trips | **10,000**, drawn from 400 recurring cab routes (fixed seed, so the same trips on every machine) |
| Pings | **15,707,734**: 1,570 per trip on average (min 959, max 2,525), one every 4 s |
| GPS noise | Jitter on every fix, 1 % bad fixes, 0.2 % teleports, 0.5 % duplicates, out-of-order batches, 1–3 dead zones (underpasses) per route, 4 % of drops without a location |
| Pings kept after cleaning | 15,441,952 (98.31 %) |
| Billable km, all trips | 368,855.72 |

### Run 1: one thread, map service healthy

| | |
|---|---|
| Engine time, all 10,000 trips | **7.4 s** |
| Per trip p50 / p95 / p99 / max | **0.62 / 1.41 / 2.43 ms** / 93 ms (the max is JVM warm-up) |
| One core computes | **1,357 trips/s = 2.1 million pings/s** |
| Peak heap used | **89 MB** (trips are made, computed and dropped one at a time) |
| Heap still in use after the run | 7 MB, mostly the route cache (7,757 roads) |
| Garbage collection | 100 runs, 146 ms in total (2 % of the run) |
| Trips COMPUTED / DEGRADED | 10,000 / 0 |

| Rule | Segments | Share |
|---|---:|---:|
| SHORT_GAP_AERIAL | 15,449,925 | 99.83 % |
| ROUTED | 23,292 | 0.15 % |
| REJECTED_SPEED | 1,831 | 0.01 % |
| PARKED | 448 | < 0.01 % |
| DEGRADED_AERIAL, REJECTED_DISTANCE | 0 | 0 % |

Flagged for review: 1,831 segments, **0.18 per trip**. Every one of them is a drop that came without a location, so the engine fell back to the *planned* stop and its planned time, which can be minutes off what really happened. Those deserve a human look.

### Routing calls (R15)

| Approach | Calls to the map service | Per trip |
|---|---:|---:|
| Route every pair of points | 15,475,496 | 1,547.55 |
| Rules first, route only real gaps (no cache) | 23,296 | 2.33 |
| **Rules first + snapped-cell cache (as built)** | **7,757** | **0.78** |

The rules settle **99.85 %** of all segments without a call. The cache then answers **66.7 %** of the remaining lookups, because cabs drive the same routes every day and a gap snaps to the same 22 m cells. That's **2,000× fewer calls** than routing every pair.

### Run 2: 12 threads, map service fails halfway

| | |
|---|---|
| Wall time, all 10,000 trips | **1.9 s = 5,129 trips/s, 8.1 million pings/s** (making the trips included) |
| Peak heap used | 239 MB |
| Map service off | after trip 5,000, back on after trip 6,000 |
| Trips computed while it was off | 999, of which **583 still COMPUTED**: every road they needed was already in the cache |
| Calls that reached the dead service | **40**, then the breaker opened and stopped calling |
| Lookups the open breaker answered at once, without calling | 2,165 |
| Trips DEGRADED (straight line × 1.4) | 1,623: 416 while it was off, 1,207 in the second after it came back while the breaker was still waiting |
| Breaker | CLOSED → OPEN → HALF_OPEN → CLOSED |
| Recovery, done like `RecoveryScanner` | 0.2 s, 1,569 calls |
| Trips COMPUTED at the end | **10,000 of 10,000** |
| km identical to run 1 | **10,000 of 10,000**: recovery restored exactly the km of the run that never had an outage |

The breaker's wait-in-open is 1 s here instead of 30 s, because the benchmark squeezes an evening of trips into seconds (A52).

### Two bugs the benchmark found (both fixed in S9)

1. **False review flags.** In rule set `v1`, a drop logged 0.1 s after a ping and 20 m away counted as 720 km/h and was flagged `REJECTED_SPEED`. That gave **0.64 flags per trip**, most of them noise. Rule set `v2` treats any move within GPS accuracy (50 m) as standing still. Labels changed, km did not: a rejected piece and a short gap both pay the straight line. Flags dropped to **0.18 per trip, all genuine**.
2. **Recovery could stall on a quiet night.** Resilience4j leaves a breaker OPEN until the *next call*, and `RecoveryScanner` only queued work once the breaker was CLOSED. With no new uploads, DEGRADED trips would never come back. The breaker now moves to HALF_OPEN on a timer, the scanner sends a few trial trips while HALF_OPEN, and trips that already have a job no longer use up the batch. `RecomputeBillingIntegrationTest` reproduces the case with zero uploads.

---

## Part 2: API load test (A02)

`bench/k6/load.js` against the full Docker stack: real HTTP, JWT checks, JSON, PostgreSQL writes, and the km recomputed on every upload.

**Traffic, for 2 minutes:**
- **20 cabs**, each driving trip after trip: 1,500 pings sent as 5 uploads of ~300 (the way a phone syncs), then one read of the trip's km
- **50 reads per second** from operators and employees (`/distance`, plus `/breakdown` for 1 in 5)

| | |
|---|---|
| HTTP requests | 12,936 = **95 per second**, **0 failed** |
| Trips ingested end to end | **939** = 6.9 trips/s, about 25,000 an hour |
| Pings ingested | **1,370,940** = **10,091 pings/s** |

| Endpoint | p50 | p95 | p99 | max | Threshold (passed) |
|---|---:|---:|---:|---:|---|
| `POST /evidence` (~300 pings, km recomputed) | 478 ms | 794 ms | **977 ms** | 1,414 ms | p99 < 3 s |
| `GET /distance` | 58 ms | 167 ms | **246 ms** | 412 ms | p99 < 500 ms |
| `GET /breakdown` (~1,500 segments) | 90 ms | 224 ms | **298 ms** | 494 ms | p99 < 1.5 s |

**What the numbers say:**
- **The upload is the expensive call, and it isn't the engine.** Part 1 shows the engine needs about 1 ms per trip. An upload stores ~300 pings, then recomputes the *whole trip so far* and saves a new run with **every** segment (up to ~1,500 rows). Five uploads per trip means 1 + 2 + 3 + 4 + 5 = 15 chunks of work for 5 chunks of data, and 20 cabs share Hikari's default pool of 10 connections. The time goes to PostgreSQL writes and waiting for a connection.
- **Reads stay fast while all that writing goes on**: p99 246 ms for km, 298 ms for a full breakdown.
- **Headroom.** 25,000 trips an hour from one app container on a laptop is more than a city's whole evening shift.
- **If it ever needs to go faster**, in order of payoff:
  1. Recompute once the trip has been quiet for a minute, instead of on every upload (debounce). That removes most of the repeat work.
  2. Store only the segments that explain something. 99.8 % are plain short-gap pieces that could be one summary row.
  3. Size the connection pool to the database, and partition the `ping` table by day.

---

## Where it stops scaling

| Limit | Measured | What it means |
|---|---|---|
| Engine CPU | 1,357 trips/s per core | Not the bottleneck. A city's whole evening shift (say 20,000 trips) is about 15 core-seconds |
| Engine memory | 89 MB peak on one thread, 239 MB on 12 | Trips are independent and short-lived. Memory grows with threads, not with trips |
| Map service | 0.78 calls per trip | A provider quota of 50 calls/s covers about 64 trips/s, so recompute storms after an outage are the first thing to throttle |
| Database writes | 10,091 pings/s ingested, upload p99 977 ms | The first real limit. Recomputing and storing every segment on every upload is the cost (see part 2). At millions of trips a month, also partition `ping` by day and tenant, or move raw pings to a log store |

---

## Run it yourself

```bash
# engine benchmark (about 1 minute, results in bench/target/bench-results.md)
mvn -q install -DskipTests
mvn -q -pl bench exec:exec

# API load test (about 3 minutes, needs the docker compose stack)
docker compose up -d --build
docker run --rm -i --network tripledger_default grafana/k6:0.54.0 run -e BASE_URL=http://app:8080 - < bench/k6/load.js
docker compose down
```

Smaller or quicker runs: `-Dbench.trips=2000` for the benchmark, `-e DURATION=30s` for k6.

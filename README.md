# TripLedger

**GPS gap backfill & billable distance engine.** Every kilometre, explained.

![CI](https://github.com/akshat-nawaria/trip-ledger/actions/workflows/ci.yml/badge.svg)

MoveInSync Backend Case Study 2026 · Case Study 1 · Advanced Track.

Rebuilds a trustworthy, explainable billable distance for every cab trip from unreliable GPS data.
Every kilometre carries the rule that produced it, the same input always gives the same answer, and paid routing calls are kept to a minimum.

> Status: **Segments S0–S3 complete** — the full distance engine works: cleaning, anchor points, rules 0–6, and the case-study underpass trip comes out at **30.61 km** (naive sum 18.4 km) with **2 routing calls**. 123 unit tests. See [docs/ROADMAP.md](docs/ROADMAP.md).

## Quick start

Prerequisites: JDK 21, Maven 3.9+ (bundled with IntelliJ IDEA), Docker Desktop, Git.

```bash
# 1. Build, run all tests, and install the core module locally
mvn install

# 2. Start the database
docker compose up -d

# 3. Run the app
mvn -pl app spring-boot:run

# 4. Check it is alive
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
```

From segment S8 onwards, `docker compose up` also builds and starts the app itself, so an evaluator needs only Docker.

## Project layout

```
core/   plain Java: the distance engine (no Spring, no database)
app/    Spring Boot: REST API, security, persistence, jobs
docs/   requirements tracker, assumptions, stack choice, roadmap, design note
```

## What works so far

| Area | Classes | Proven by |
|---|---|---|
| Exact distances | `GeoPoint` (micro-degrees), `Distance` (integer mm), `Haversine` (StrictMath) | `GeoPointTest`, `DistanceTest`, `HaversineTest` |
| One place for every threshold | `BillingRules` (case-study defaults + builder) | `BillingRulesTest` |
| Anchor points | `EmployeeAnchorResolver` (actual → profile home → planned), `NoShowAnchorResolver` (behind a flag), `OfficeAnchorResolver` (400 m rule) | One test class per resolver |
| Segment rules 0–6 | `ImpossibleSpeedRule`, `ShortGapRule`, `ParkedRule`, `RoutingUnavailableRule`, `RouteTooLongRule`, `RouteTooFastRule`, `RoutedRule` in `RuleSet.v1()` | `RuleSetTest` (every boundary: 69/70 s, 4.9/5.1 km/h, 100 km) |
| The engine | `DistanceEngine.compute(evidence, routeLookup)` → `TripDistance` with a label on every segment | `DistanceEngineTest` (underpass 18.4 → 30.61 km, outage → DEGRADED, shuffled input → identical result) |
| GPS cleaning | `InvalidCoordinateFilter` → `DuplicateFilter` → `TimeOrderFilter` → `AccuracyFilter` → `TeleportFilter`, run by `CleaningPipeline` | One test class per filter + `CleaningPipelineTest` (shuffled 1,000× → same result) |

Run only the engine tests (fast, no database needed):

```bash
mvn -pl core test
```

## Documents

- [Design note](docs/DESIGN.md) — architecture, data model, APIs, rule order, failure modes, trade-offs
- [Requirements tracker](docs/REQUIREMENTS.md) — every PDF item, where it is built, how it is proved
- [Assumptions](docs/ASSUMPTIONS.md) — decisions where the PDF is open
- [Technology choice](docs/STACK.md) — why Java 21 + Spring Boot
- [Roadmap](docs/ROADMAP.md) — the 11 build segments

## Sections still to come

- API reference and sample calls (S5, S10)
- Benchmark results and machine (S9)
- Trade-offs and what was left out (S10)
- Demo video (S10)

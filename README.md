# TripLedger

**GPS gap backfill & billable distance engine.** Every kilometre, explained.

![CI](https://github.com/akshat-nawaria/trip-ledger/actions/workflows/ci.yml/badge.svg)

MoveInSync Backend Case Study 2026 · Case Study 1 · Advanced Track.

Rebuilds a trustworthy, explainable billable distance for every cab trip from unreliable GPS data.
Every kilometre carries the rule that produced it, the same input always gives the same answer, and paid routing calls are kept to a minimum.

> Status: **Segments S0–S4 complete** — the distance engine works end to end (cleaning, anchors, rules 0–6; underpass trip **18.4 → 30.61 km** with **2 routing calls**), and the routing layer is in: grid-snapped Caffeine cache, Resilience4j circuit breaker and a mock provider with an off switch. 20 trips over the same gap cost **1** provider call. 157 tests. See [docs/ROADMAP.md](docs/ROADMAP.md).

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
app/    Spring Boot: routing layer (cache, breaker, mock provider); REST API, security, persistence, jobs to come
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

## Sections still to come

- API reference and sample calls (S5, S10)
- Benchmark results and machine (S9)
- Trade-offs and what was left out (S10)
- Demo video (S10)

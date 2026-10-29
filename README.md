# TripLedger

**GPS gap backfill & billable distance engine.** Every kilometre, explained.

![CI](https://github.com/akshat-nawaria/trip-ledger/actions/workflows/ci.yml/badge.svg)

MoveInSync Backend Case Study 2026 · Case Study 1 · Advanced Track.

Rebuilds a trustworthy, explainable billable distance for every cab trip from unreliable GPS data.
Every kilometre carries the rule that produced it, the same input always gives the same answer, and paid routing calls are kept to a minimum.

> Status: **Segments S0–S6 complete.** The distance engine works end to end (underpass trip **18.4 → 30.61 km** with **2 routing calls**), routing is cached behind a circuit breaker (20 trips over one gap = **1** paid call), and a secured REST API on PostgreSQL: JWT roles (employee / operator / admin), strict per-company isolation, per-company billing settings with versions, and uniform errors with trace ids. **213 tests.** See [docs/ROADMAP.md](docs/ROADMAP.md).

## Quick start

Prerequisites: JDK 21, Maven 3.9+ (bundled with IntelliJ IDEA), Docker Desktop, Git.

```bash
# 1. Start the database (the app's tests need it too)
docker compose up -d

# 2. Build, run all tests, and install the core module locally
mvn install

# 3. Run the app
mvn -pl app spring-boot:run

# 4. Check it is alive, then open the API docs
curl http://localhost:8080/actuator/health
# browser: http://localhost:8080/swagger-ui.html
```

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
| `GET / PUT /v1/tenant/config` | ADMIN | Your company's billing settings (no-show flag, accuracy, speed, office radius, max route). Every save is a new version |
| `POST /v1/dev/tokens` | anyone, **local only** | A token for any tenant and role, for trying things out |

Another company's trip is always **404**, never 403, so its existence is not revealed. Errors are RFC 7807 `application/problem+json` with a stable `code` and a `traceId` (also in the `X-Trace-Id` header and in every log line of that request).

### Troubleshooting

| You see | Why | Fix |
|---|---|---|
| `Connection to localhost:5432 refused` | Database not running, or Docker Desktop lost the port after sleep/restart | `docker compose down` then `docker compose up -d`, wait for `(healthy)` |
| `ClassNotFoundException: com.tripledger.core...` when running only `app` | `-pl app` uses the copy of `core` saved by the last `mvn install` | `mvn install -DskipTests`, then run again |
| `Port 8080 was already in use` | An earlier run is still alive | Stop it with Ctrl + C, or find it: `netstat -ano \| findstr :8080`, then `taskkill //PID <pid> //F` |
| `401 UNAUTHORIZED` | Missing, expired or foreign token | Get a fresh one from `POST /v1/dev/tokens` |

From segment S8 onwards, `docker compose up` also builds and starts the app itself, so an evaluator needs only Docker.

## Project layout

```
core/   plain Java: the distance engine (no Spring, no database)
app/    Spring Boot: REST API, JWT security + tenants, PostgreSQL (Flyway + Spring JDBC), routing layer; jobs to come
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

## Sections still to come

- API reference and sample calls (S5, S10)
- Benchmark results and machine (S9)
- Trade-offs and what was left out (S10)
- Demo video (S10)

# Assumptions

The case study leaves some rules open. These are the decisions taken, so the behaviour is deliberate and testable.
Every value marked *configurable* lives in `application.yml` (and in the core `BillingRules` config record).

| # | Topic | Assumption | Configurable |
|---|-------|------------|--------------|
| A1 | Low-accuracy fix | A ping whose reported accuracy is worse than **50 m** is dropped | Yes |
| A2 | Teleport speed limit | **120 km/h** (PDF default) | Yes |
| A3 | "Plausible speed" for a short gap | Implied straight-line speed ≤ the speed limit | — |
| A4 | "Long gap" | Any gap of **70 s or more** | Yes |
| A5 | Parked speed | Below **5 km/h** (PDF default) | Yes |
| A6 | Distance after a rejected route | Plain straight-line (haversine) distance, flagged for review. We never pay for a route we rejected | — |
| A7 | Impossible straight-line speed | If even the straight line is faster than the limit, the segment is rejected **without** calling the routing provider (a road is never shorter than a straight line) | — |
| A8 | Logout trips | The office anchor is placed at **trip start** for logout trips (office → home) and at **trip end** for login trips | — |
| A9 | Valid point | Not null, inside lat/lon bounds, not (0, 0), timestamp inside the trip window | — |
| A10 | Device clock skew | Pings outside the trip window ± 10 minutes are ignored | Yes |
| A11 | Units | All distances are stored as **integer millimetres** (`long`) | — |
| A12 | Rounding | Billable km = total millimetres rounded **once**, HALF_EVEN, to 10 m (shown as 0.01 km) | — |
| A13 | Money | `BigDecimal`, HALF_EVEN, to the paisa | — |
| A14 | Earth radius | 6,371,008.8 m (mean radius), fixed in the rule set | — |
| A15 | Routing provider | A mock provider is used for the demo and tests; the interface allows Google, OSRM or others | — |
| A16 | Rule-set version | `v1`; a result always stores the rule-set version and a hash of the config it used | — |
| A17 | Duplicate pings | One ping per timestamp; on conflict the most accurate wins, then lowest device sequence | — |
| A18 | Invalid coordinates | Out-of-range points and (0, 0) are dropped before any other step | — |
| A20 | Who gets an employee anchor | Only employees with a PICKUP or DROP milestone. A roster entry with no milestone is no proof the employee rode, so no detour is added | — |
| A21 | Same-second points | An anchor and a ping at the same second within GPS accuracy (50 m) are treated as the same spot (speed 0); further apart at the same instant is impossible | — |
| A22 | Office event sent twice | Latest TRIP_END (login) / earliest TRIP_START (logout) is used; ones with a timestamp win | — |
| A23 | Missing office event | The planned office is anchored at the window end (login) or window start (logout) | — |
| A19 | Teleport re-anchor | 3 consistent suspicious pings in a row replace the accepted pings they contradict, but only if those are fewer | Yes |
| A24 | Route cache grid | Both ends of a gap are snapped to a 0.0002° grid (~22 m) and the provider is asked about the **cell centres**. The answer then depends only on the cells, so trip order never changes a bill. Cost: at most ~31 m per routed gap (two half-diagonals) | Yes |
| A25 | Cache vs. breaker order | Cache sits in front of the circuit breaker, so known routes are still answered during an outage. Failed calls are never cached | — |
| A26 | Circuit breaker | Last 10 calls, at least 5, opens at 50 % failures, stays open 30 s, then 3 trial calls | Yes |
| A27 | No request batching | Rules 0–2 settle most gaps before any lookup, so a trip needs only ~2 routes. Batching would force routing every gap up front, including ones the rules never route | — |
| A29 | Split uploads | A trip may arrive in several uploads; evidence is merged and duplicates ignored. Trip type, window and office are fixed by the first upload; a later upload that disagrees is rejected (409) | — |
| A30 | Trip window length | At most 24 hours; anything longer is rejected as a client error (400) | — |
| A31 | Timestamp precision | Microseconds (what PostgreSQL stores); finer input is truncated once, on arrival | — |
| A32 | Coordinates at the API | Not range-checked on upload. Junk GPS is normal and is removed (and counted) by the cleaning filters instead | — |
| A33 | Idempotency-Key scope | Per trip. Same key + same body = replay (202, nothing redone); same key + different body = 409 | — |
| A34 | When km are computed | Straight after each upload (S5). S7 moves this to a background job; the API does not change | — |
| A35 | Token signing | HS256 with a shared secret (`JWT_SECRET`), issuer `tripledger`. Production would verify the company login system's tokens with its public keys; only the decoder bean changes | Yes |
| A36 | Dev token endpoint | `POST /v1/dev/tokens` exists only while `tripledger.security.dev-tokens=true` (local default). Must be false anywhere real | Yes |
| A37 | "Own trip" for an employee | The employee's ref appears in the trip's milestones or plan. Anything else is reported as 404 | — |
| A38 | Tenant settings | Five values per company: no-show flag, max accuracy, max speed, office radius, max route. Each save is a new version; it applies from a trip's next computation and never edits saved runs | — |
| A39 | Tenant isolation | Tenant comes only from the token; every query filters on it; a cross-tenant read is 404. PostgreSQL row-level security is not built (see DESIGN trade-offs) | — |
| A40 | When km are computed (updates A34) | Straight after each upload (fast path), from a job queued in the same transaction; if that fails or a worker is busy with the trip, the background worker finishes it | Yes |
| A41 | Job retries | Back-off 10 s, 30 s, 90 s, ... capped at 10 min; FAILED after 5 attempts; lease 60 s | Yes |
| A42 | Recovery | Every minute, only while the routing breaker is CLOSED, up to 100 DEGRADED trips are re-queued | Yes |
| A43 | Billing | A trip goes on one invoice, once; billing freezes its current run and km. A DEGRADED trip may be billed (provisional) | — |
| A44 | Adjustments | Only a COMPUTED run creates one, and only when billable km (2 decimals) differ from invoice + earlier adjustments; signed, never edited | — |
| A45 | Pinned routes | A gap with exactly the same start and end as one an earlier run routed reuses that answer; the newest wins | — |
| A46 | Metrics endpoint | `/actuator/prometheus` needs no token, like `/actuator/health`. It only holds counts and timings, never a tenant or trip id. In production it would sit behind the internal network | — |
| A47 | Mock outage switch | `PUT /v1/admin/routing/mock` works only while the provider is the mock one; with a real map service it is refused (400). It is for demos and tests | — |
| A48 | Demo console | Gets its own ADMIN + OPERATOR token from the dev token endpoint, so it only works where that endpoint is on (local / Docker demo) | — |
| A49 | Metric tags | No tag per tenant or trip (one time series per trip would overload Prometheus). Per-trip detail lives in the database and the logs | — |
| A28 | Provider contract | A provider returns a real distance or throws `RoutingException`. Any other exception is a bug and fails loudly instead of quietly degrading a bill | — |

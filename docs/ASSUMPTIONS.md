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

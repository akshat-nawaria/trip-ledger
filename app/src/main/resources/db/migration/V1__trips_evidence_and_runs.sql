-- S5: where trips, their raw evidence and every distance computation live.
--
-- Conventions used everywhere:
--   * coordinates are integer micro-degrees (lat_e6 / lon_e6), same as GeoPoint in core
--   * distances are integer millimetres (*_mm), same as Distance in core
--   * every table carries tenant_id so row-level security can be switched on later (S6)
--   * evidence is append-only: we never update or delete a ping, we only add


-- one row per trip. The trip details (type, window, office) come from the first upload and
-- every later upload for the same trip has to agree with them.
CREATE TABLE trip (
    tenant_id      TEXT        NOT NULL,
    trip_id        TEXT        NOT NULL,
    trip_type      TEXT        NOT NULL CHECK (trip_type IN ('LOGIN', 'LOGOUT')),
    window_start   TIMESTAMPTZ NOT NULL,
    window_end     TIMESTAMPTZ NOT NULL,
    office_lat_e6  BIGINT      NOT NULL,
    office_lon_e6  BIGINT      NOT NULL,
    status         TEXT        NOT NULL CHECK (status IN ('RECEIVED', 'COMPUTED', 'DEGRADED')),
    latest_run_id  UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, trip_id),
    CHECK (window_end > window_start)
);

-- "show me every DEGRADED trip" - the recovery backlog
CREATE INDEX trip_status_idx ON trip (tenant_id, status, updated_at);


-- raw GPS pings. The unique key is what makes a re-sent ping a no-op instead of a double count.
CREATE TABLE ping (
    id           BIGSERIAL   PRIMARY KEY,
    tenant_id    TEXT        NOT NULL,
    trip_id      TEXT        NOT NULL,
    device_ts    TIMESTAMPTZ NOT NULL,
    lat_e6       BIGINT      NOT NULL,
    lon_e6       BIGINT      NOT NULL,
    accuracy_m   INT         NOT NULL CHECK (accuracy_m >= 0),
    device_seq   BIGINT      NOT NULL,
    FOREIGN KEY (tenant_id, trip_id) REFERENCES trip (tenant_id, trip_id),
    UNIQUE (tenant_id, trip_id, device_ts, lat_e6, lon_e6)
);


-- pickups, drops, no-shows, trip start/end. Point and time can be missing (driver apps do that),
-- so the dedupe key is built in Java from all the fields instead of a plain multi-column unique.
CREATE TABLE milestone (
    id            BIGSERIAL   PRIMARY KEY,
    tenant_id     TEXT        NOT NULL,
    trip_id       TEXT        NOT NULL,
    dedupe_key    TEXT        NOT NULL,
    employee_ref  TEXT,
    type          TEXT        NOT NULL CHECK (type IN ('TRIP_START', 'PICKUP', 'DROP', 'NO_SHOW', 'TRIP_END')),
    lat_e6        BIGINT,
    lon_e6        BIGINT,
    event_ts      TIMESTAMPTZ,
    FOREIGN KEY (tenant_id, trip_id) REFERENCES trip (tenant_id, trip_id),
    UNIQUE (tenant_id, trip_id, dedupe_key)
);


-- the roster plan (planned riders) or the profile home + sign-in time (ad-hoc riders)
CREATE TABLE planned_stop (
    id            BIGSERIAL   PRIMARY KEY,
    tenant_id     TEXT        NOT NULL,
    trip_id       TEXT        NOT NULL,
    dedupe_key    TEXT        NOT NULL,
    employee_ref  TEXT        NOT NULL,
    rider_type    TEXT        NOT NULL CHECK (rider_type IN ('PLANNED', 'ADHOC')),
    lat_e6        BIGINT,
    lon_e6        BIGINT,
    planned_ts    TIMESTAMPTZ,
    FOREIGN KEY (tenant_id, trip_id) REFERENCES trip (tenant_id, trip_id),
    UNIQUE (tenant_id, trip_id, dedupe_key)
);


-- one row per Idempotency-Key. Same key + same body = a retry, answered without redoing anything.
-- Same key + a different body = a client bug, rejected with 409.
CREATE TABLE ingest_request (
    tenant_id        TEXT        NOT NULL,
    trip_id          TEXT        NOT NULL,
    idempotency_key  TEXT        NOT NULL,
    request_hash     TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, trip_id, idempotency_key),
    FOREIGN KEY (tenant_id, trip_id) REFERENCES trip (tenant_id, trip_id)
);


-- every time the engine runs for a trip we keep the full answer. Old runs are never edited,
-- so we can always show exactly what a past bill was based on.
CREATE TABLE computation_run (
    id                   UUID          PRIMARY KEY,
    tenant_id            TEXT          NOT NULL,
    trip_id              TEXT          NOT NULL,
    input_hash           TEXT          NOT NULL,
    rule_version         TEXT          NOT NULL,
    status               TEXT          NOT NULL CHECK (status IN ('COMPUTED', 'DEGRADED')),
    total_mm             BIGINT        NOT NULL CHECK (total_mm >= 0),
    billable_km          NUMERIC(12,2) NOT NULL,
    route_requests       INT           NOT NULL,
    cache_hits           INT           NOT NULL,
    provider_calls       INT           NOT NULL,
    routing_unavailable  INT           NOT NULL,
    cleaning_json        JSONB         NOT NULL,
    anchors_json         JSONB         NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, trip_id) REFERENCES trip (tenant_id, trip_id)
);

-- the same evidence under the same rules can only ever produce ONE good run. A DEGRADED run is
-- allowed to repeat on purpose: once routing is back, the same input should give a better answer.
CREATE UNIQUE INDEX computation_run_once_idx
    ON computation_run (tenant_id, trip_id, input_hash, rule_version)
    WHERE status = 'COMPUTED';

CREATE INDEX computation_run_trip_idx ON computation_run (tenant_id, trip_id, created_at);

ALTER TABLE trip
    ADD CONSTRAINT trip_latest_run_fk FOREIGN KEY (latest_run_id) REFERENCES computation_run (id);


-- every measured piece of a run, with the rule that measured it. This is the "why" behind a bill.
CREATE TABLE run_segment (
    run_id       UUID        NOT NULL REFERENCES computation_run (id),
    seq          INT         NOT NULL,
    from_ts      TIMESTAMPTZ NOT NULL,
    to_ts        TIMESTAMPTZ NOT NULL,
    from_lat_e6  BIGINT      NOT NULL,
    from_lon_e6  BIGINT      NOT NULL,
    to_lat_e6    BIGINT      NOT NULL,
    to_lon_e6    BIGINT      NOT NULL,
    distance_mm  BIGINT      NOT NULL CHECK (distance_mm >= 0),
    rule         TEXT        NOT NULL,
    PRIMARY KEY (run_id, seq)
);

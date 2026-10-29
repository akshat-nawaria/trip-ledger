-- S7: background jobs, billing, signed adjustments and pinned routes.


-- The work queue. One row = "compute this trip". Lives in Postgres on purpose: it's written in the
-- SAME transaction as the evidence (so an upload can never be stored without its job, or the other
-- way round), and Postgres gives us everything a queue needs: row locks, SKIP LOCKED, leases.
--
--   PENDING  -> waiting (run_after says when it may run - used for retry back-off)
--   RUNNING  -> claimed by a worker until lease_until. If that worker dies, the lease runs out
--               and another worker picks the job up again.
--   DONE / FAILED -> finished. Kept for a while as a record (last_error says why it failed).
--
-- No foreign key to trip on purpose: the queue must be able to hold (and fail cleanly on) a job
-- for a trip that isn't there, instead of the insert itself blowing up.
CREATE TABLE compute_job (
    id           BIGSERIAL   PRIMARY KEY,
    tenant_id    TEXT        NOT NULL,
    trip_id      TEXT        NOT NULL,
    reason       TEXT        NOT NULL CHECK (reason IN ('UPLOAD', 'RECOVERY', 'MANUAL')),
    status       TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts     INT         NOT NULL DEFAULT 0,
    run_after    TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until  TIMESTAMPTZ,
    last_error   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- at most ONE waiting job per trip: ten uploads in a row still mean one computation, not ten
CREATE UNIQUE INDEX compute_job_one_pending_idx ON compute_job (tenant_id, trip_id) WHERE status = 'PENDING';
CREATE INDEX compute_job_claim_idx ON compute_job (status, run_after);


-- Billing. Once a trip is on an invoice, that invoice is closed: billed_run_id and billed_km never
-- change again. Anything that moves the km afterwards becomes an adjustment line instead.
ALTER TABLE trip
    ADD COLUMN invoice_id     TEXT,
    ADD COLUMN billed_run_id  UUID REFERENCES computation_run (id),
    ADD COLUMN billed_km      NUMERIC(12,2),
    ADD COLUMN billed_at      TIMESTAMPTZ;


-- A signed correction for a billed trip: "+1.80 km" or "-0.24 km", to go on the next invoice.
-- One per run at most (to_run_id is unique), so recomputing can never produce the same line twice.
CREATE TABLE invoice_adjustment (
    id           UUID          PRIMARY KEY,
    tenant_id    TEXT          NOT NULL,
    trip_id      TEXT          NOT NULL,
    invoice_id   TEXT          NOT NULL,
    delta_km     NUMERIC(12,2) NOT NULL,
    delta_mm     BIGINT        NOT NULL,
    from_run_id  UUID          NOT NULL REFERENCES computation_run (id),
    to_run_id    UUID          NOT NULL UNIQUE REFERENCES computation_run (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, trip_id) REFERENCES trip (tenant_id, trip_id)
);

CREATE INDEX invoice_adjustment_trip_idx ON invoice_adjustment (tenant_id, trip_id, created_at);


-- Pinned routes: every road distance a run actually used. A recompute of the same trip replays these
-- instead of asking the provider again - so a map update (or an outage) can't move an old bill.
CREATE TABLE run_route (
    run_id       UUID   NOT NULL REFERENCES computation_run (id),
    seq          INT    NOT NULL,
    from_lat_e6  BIGINT NOT NULL,
    from_lon_e6  BIGINT NOT NULL,
    to_lat_e6    BIGINT NOT NULL,
    to_lon_e6    BIGINT NOT NULL,
    distance_mm  BIGINT NOT NULL CHECK (distance_mm >= 0),
    PRIMARY KEY (run_id, seq)
);

-- how many road distances a run took from pinned routes instead of the provider
ALTER TABLE computation_run ADD COLUMN routes_replayed INT NOT NULL DEFAULT 0;

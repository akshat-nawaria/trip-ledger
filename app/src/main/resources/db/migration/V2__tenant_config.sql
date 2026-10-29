-- S6: per-tenant billing settings.
--
-- Every change is a NEW row with the next version number - rows are never updated. So for any old
-- bill we can say exactly which settings it was computed with (computation_run.config_version).

CREATE TABLE tenant_config (
    tenant_id               TEXT             NOT NULL,
    version                 INT              NOT NULL CHECK (version > 0),
    no_show_anchor_enabled  BOOLEAN          NOT NULL,
    max_accuracy_m          INT              NOT NULL,
    max_speed_kmph          DOUBLE PRECISION NOT NULL,
    office_radius_m         INT              NOT NULL,
    max_route_km            INT              NOT NULL,
    changed_by              TEXT             NOT NULL,
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, version)
);

-- which settings a run used. 0 = the case-study defaults (the tenant never changed anything).
-- Existing runs from S5 were all computed with the defaults, so 0 is also correct for them.
ALTER TABLE computation_run ADD COLUMN config_version INT NOT NULL DEFAULT 0;

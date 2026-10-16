-- V12: trip assembly -- the current vehicle and driver on a trip, plus an
-- append-only history of every assignment.
--
-- Vertical fragmentation: a trip's CURRENT vehicle and driver are volatile
-- (a driver is swapped, a truck breaks down), so they belong in the trip's
-- STATE fragment, next to position and status. They are resolved by Lamport
-- timestamp like the rest of the row -- the existing
-- trg_resolve_trip_state_conflict needs no change, because it replaces the
-- whole row. The columns are nullable: a trip can be created first and have
-- a vehicle and driver assigned afterwards.
--
-- The HISTORY of who drove which trip is an append-only EVENT log
-- (trip_assignment), deduped by primary key like handover, milestone and
-- incident. Nothing is ever updated or deleted, so a driver can appear on
-- many trips over time and a mid-trip swap leaves a record. Whether a driver
-- or vehicle is "already on an active trip" is checked by the application at
-- the site that holds the trip, not by a unique index: a unique index on a
-- replicated table would make two simultaneous assignments at different sites
-- fail while being APPLIED, which halts replication.
--
-- ORDER MATTERS, and there must be no application writes in between: apply
-- this file at ALL FOUR sites back to back (it is identical everywhere).
-- Logical replication does not replicate DDL, and a trip_state row that
-- arrives carrying the new columns at a site that lacks them stalls that
-- site's subscription. Then run infra/k8s/extend-replication-v12.sh to
-- publish trip_assignment.

ALTER TABLE trip_state
    ADD COLUMN vehicle_id UUID,
    ADD COLUMN driver_id  UUID;

ALTER TABLE trip_state
    ADD CONSTRAINT trip_state_vehicle_id_fkey
        FOREIGN KEY (vehicle_id) REFERENCES vehicle_profile (vehicle_id),
    ADD CONSTRAINT trip_state_driver_id_fkey
        FOREIGN KEY (driver_id) REFERENCES driver_profile (driver_id);

CREATE TABLE trip_assignment (
    assignment_id  UUID PRIMARY KEY,
    site_id        TEXT NOT NULL,
    trip_id        UUID NOT NULL,
    resource_type  TEXT NOT NULL CHECK (resource_type IN ('VEHICLE', 'DRIVER')),
    resource_id    UUID NOT NULL,
    action         TEXT NOT NULL CHECK (action IN ('ASSIGNED', 'RELEASED')),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_assignment_trip     ON trip_assignment (trip_id);
CREATE INDEX idx_trip_assignment_resource ON trip_assignment (resource_id);

-- Dedup by primary key, exactly like skip_duplicate_milestone.
CREATE OR REPLACE FUNCTION skip_duplicate_trip_assignment() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM trip_assignment WHERE assignment_id = NEW.assignment_id) THEN
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dedupe_trip_assignment
  BEFORE INSERT ON trip_assignment
  FOR EACH ROW EXECUTE FUNCTION skip_duplicate_trip_assignment();
-- Apply workers run with session_replication_role = replica, so without
-- ENABLE ALWAYS this trigger would be skipped for replicated rows.
ALTER TABLE trip_assignment ENABLE ALWAYS TRIGGER trg_dedupe_trip_assignment;
-- Apply workers run with an empty search_path; pin it (bug #1).
ALTER FUNCTION skip_duplicate_trip_assignment() SET search_path = public;

-- V2/V4 snapshot grants do not cover tables created later (the bug #11
-- trap). Append-only: read and insert, never update or delete.
GRANT SELECT, INSERT ON trip_assignment TO app_user;

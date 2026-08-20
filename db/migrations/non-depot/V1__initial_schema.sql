-- V1: Initial schema — Trip fragments + Handover event log + conflict infra.
-- Applied identically at every site (DDL is not replicated by Postgres
-- logical replication, so each site runs its own Flyway history against
-- its own database, all pointed at this same source-controlled SQL).

CREATE TABLE trip_master (
    trip_id              UUID PRIMARY KEY,
    origin_site_id       TEXT NOT NULL,
    destination_site_id  TEXT NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE trip_state (
    trip_id           UUID PRIMARY KEY,
    status            TEXT NOT NULL DEFAULT 'Planned',
    current_site_id   TEXT NOT NULL,
    lamport_ts        BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE handover (
    event_id        UUID PRIMARY KEY,
    trip_id         UUID NOT NULL,
    from_site_id    TEXT NOT NULL,
    to_site_id      TEXT NOT NULL,
    verified_by     TEXT NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conflict_exceptions (
    id              BIGSERIAL PRIMARY KEY,
    table_name      TEXT,
    row_key         TEXT,
    local_row       JSONB,
    incoming_row    JSONB,
    detected_at     TIMESTAMPTZ DEFAULT now()
);

-- Idempotency trigger for the event log
CREATE OR REPLACE FUNCTION skip_duplicate_handover() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM handover WHERE event_id = NEW.event_id) THEN
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dedupe_handover
  BEFORE INSERT ON handover
  FOR EACH ROW EXECUTE FUNCTION skip_duplicate_handover();

-- CRITICAL: apply workers run with session_replication_role = 'replica',
-- so a normal trigger is skipped for rows arriving via replication.
-- ENABLE ALWAYS makes it fire on both local AND replicated writes.
ALTER TABLE handover ENABLE ALWAYS TRIGGER trg_dedupe_handover;

-- Postgres runs replication apply workers with an EMPTY search_path.
-- Unqualified table names in a trigger fail ONLY when fired by
-- replication, not on local writes. Pin it explicitly. (Bug #1 found
-- during testing — see docs/testing/.)
ALTER FUNCTION skip_duplicate_handover() SET search_path = public;

-- Conflict-resolution trigger for the state fragment
CREATE OR REPLACE FUNCTION resolve_trip_state_conflict() RETURNS trigger AS $$
BEGIN
  IF NEW.lamport_ts < OLD.lamport_ts
     OR (NEW.lamport_ts = OLD.lamport_ts AND NEW.current_site_id > OLD.current_site_id) THEN
    INSERT INTO conflict_exceptions(table_name, row_key, local_row, incoming_row)
      VALUES ('trip_state', OLD.trip_id::text, row_to_json(OLD), row_to_json(NEW));
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resolve_trip_state_conflict
  BEFORE UPDATE ON trip_state
  FOR EACH ROW EXECUTE FUNCTION resolve_trip_state_conflict();
ALTER TABLE trip_state ENABLE ALWAYS TRIGGER trg_resolve_trip_state_conflict;
ALTER FUNCTION resolve_trip_state_conflict() SET search_path = public;

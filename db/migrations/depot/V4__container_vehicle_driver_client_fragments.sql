-- BorderFlow K8s schema extension — Container, Vehicle/Driver, Client/Consignment
-- Same conventions as 01_schema.sql: identical content applied at every site
-- (Client_Contact is the one exception — see note below, Depot-only).
-- Apply this via `kubectl exec ... psql` to each already-running pod, since
-- docker-entrypoint-initdb.d only runs once on an empty data directory.

-- ════════════════════════════════════════════════════════════════════════
-- MASTER FRAGMENTS — created once at Depot, replicated read-only elsewhere
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE container_master (
    container_id      UUID PRIMARY KEY,
    container_number  TEXT UNIQUE NOT NULL,
    consignment_id    UUID NOT NULL,
    size              TEXT NOT NULL
);

CREATE TABLE vehicle_profile (
    vehicle_id          UUID PRIMARY KEY,
    registration_number TEXT UNIQUE NOT NULL,
    capacity            NUMERIC NOT NULL
);

CREATE TABLE driver_profile (
    driver_id       UUID PRIMARY KEY,
    name            TEXT NOT NULL,
    license_number  TEXT UNIQUE NOT NULL,
    phone           TEXT
);

CREATE TABLE client_core (
    client_id  UUID PRIMARY KEY,
    name       TEXT NOT NULL
);

CREATE TABLE consignment (
    consignment_id  UUID PRIMARY KEY,
    client_id       UUID NOT NULL REFERENCES client_core(client_id),
    description     TEXT
);

-- ════════════════════════════════════════════════════════════════════════
-- PII FRAGMENT — DEPOT ONLY. This file is the Depot-specific variant;
-- the base 02_extend_schema.sql (used at Border/Port/Destination) keeps
-- this commented out. Not replicated to operational sites by design (§7).
-- ════════════════════════════════════════════════════════════════════════
CREATE TABLE client_contact (
    client_id     UUID PRIMARY KEY REFERENCES client_core(client_id),
    contact_info  TEXT NOT NULL
);

-- ════════════════════════════════════════════════════════════════════════
-- STATE FRAGMENTS — multi-leader, single-writer-at-a-time via ownership token
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE container_state (
    container_id      UUID PRIMARY KEY,
    status            TEXT NOT NULL DEFAULT 'AtOrigin',
    current_site_id   TEXT NOT NULL,
    last_milestone_id UUID,
    lamport_ts        BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_site   TEXT
);

CREATE TABLE vehicle_availability (
    vehicle_id        UUID PRIMARY KEY,
    status            TEXT NOT NULL DEFAULT 'Available',
    current_site_id   TEXT NOT NULL,
    lamport_ts        BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE driver_availability (
    driver_id         UUID PRIMARY KEY,
    status            TEXT NOT NULL DEFAULT 'Available',
    current_site_id   TEXT NOT NULL,
    lamport_ts        BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ════════════════════════════════════════════════════════════════════════
-- EVENT LOGS — append-only, horizontally partitioned by originating site,
-- deduped by primary key event id (same pattern as `handover`)
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE milestone (
    milestone_id   UUID PRIMARY KEY,
    site_id        TEXT NOT NULL,
    trip_id        UUID NOT NULL,
    milestone_type TEXT NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE incident (
    incident_id   UUID PRIMARY KEY,
    site_id       TEXT NOT NULL,
    trip_id       UUID NOT NULL,
    description   TEXT NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE trip_container (
    trip_id       UUID NOT NULL,
    container_id  UUID NOT NULL,
    site_id       TEXT NOT NULL,
    linked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, container_id)
);

-- ════════════════════════════════════════════════════════════════════════
-- DEDUP TRIGGERS — event logs (mirrors trg_dedupe_handover exactly)
-- ════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION skip_duplicate_milestone() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM milestone WHERE milestone_id = NEW.milestone_id) THEN
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dedupe_milestone
  BEFORE INSERT ON milestone
  FOR EACH ROW EXECUTE FUNCTION skip_duplicate_milestone();
ALTER TABLE milestone ENABLE ALWAYS TRIGGER trg_dedupe_milestone;
ALTER FUNCTION skip_duplicate_milestone() SET search_path = public;

CREATE OR REPLACE FUNCTION skip_duplicate_incident() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM incident WHERE incident_id = NEW.incident_id) THEN
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dedupe_incident
  BEFORE INSERT ON incident
  FOR EACH ROW EXECUTE FUNCTION skip_duplicate_incident();
ALTER TABLE incident ENABLE ALWAYS TRIGGER trg_dedupe_incident;
ALTER FUNCTION skip_duplicate_incident() SET search_path = public;

CREATE OR REPLACE FUNCTION skip_duplicate_trip_container() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM trip_container WHERE trip_id = NEW.trip_id AND container_id = NEW.container_id) THEN
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dedupe_trip_container
  BEFORE INSERT ON trip_container
  FOR EACH ROW EXECUTE FUNCTION skip_duplicate_trip_container();
ALTER TABLE trip_container ENABLE ALWAYS TRIGGER trg_dedupe_trip_container;
ALTER FUNCTION skip_duplicate_trip_container() SET search_path = public;

-- ════════════════════════════════════════════════════════════════════════
-- CONFLICT-RESOLUTION TRIGGERS — state fragments (mirrors
-- resolve_trip_state_conflict exactly, same Lamport comparison rule)
-- ════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION resolve_container_state_conflict() RETURNS trigger AS $$
BEGIN
  IF NEW.lamport_ts < OLD.lamport_ts
     OR (NEW.lamport_ts = OLD.lamport_ts AND NEW.current_site_id > OLD.current_site_id) THEN
    INSERT INTO conflict_exceptions(table_name, row_key, local_row, incoming_row)
      VALUES ('container_state', OLD.container_id::text, row_to_json(OLD), row_to_json(NEW));
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resolve_container_state_conflict
  BEFORE UPDATE ON container_state
  FOR EACH ROW EXECUTE FUNCTION resolve_container_state_conflict();
ALTER TABLE container_state ENABLE ALWAYS TRIGGER trg_resolve_container_state_conflict;
ALTER FUNCTION resolve_container_state_conflict() SET search_path = public;

CREATE OR REPLACE FUNCTION resolve_vehicle_availability_conflict() RETURNS trigger AS $$
BEGIN
  IF NEW.lamport_ts < OLD.lamport_ts
     OR (NEW.lamport_ts = OLD.lamport_ts AND NEW.current_site_id > OLD.current_site_id) THEN
    INSERT INTO conflict_exceptions(table_name, row_key, local_row, incoming_row)
      VALUES ('vehicle_availability', OLD.vehicle_id::text, row_to_json(OLD), row_to_json(NEW));
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resolve_vehicle_availability_conflict
  BEFORE UPDATE ON vehicle_availability
  FOR EACH ROW EXECUTE FUNCTION resolve_vehicle_availability_conflict();
ALTER TABLE vehicle_availability ENABLE ALWAYS TRIGGER trg_resolve_vehicle_availability_conflict;
ALTER FUNCTION resolve_vehicle_availability_conflict() SET search_path = public;

CREATE OR REPLACE FUNCTION resolve_driver_availability_conflict() RETURNS trigger AS $$
BEGIN
  IF NEW.lamport_ts < OLD.lamport_ts
     OR (NEW.lamport_ts = OLD.lamport_ts AND NEW.current_site_id > OLD.current_site_id) THEN
    INSERT INTO conflict_exceptions(table_name, row_key, local_row, incoming_row)
      VALUES ('driver_availability', OLD.driver_id::text, row_to_json(OLD), row_to_json(NEW));
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resolve_driver_availability_conflict
  BEFORE UPDATE ON driver_availability
  FOR EACH ROW EXECUTE FUNCTION resolve_driver_availability_conflict();
ALTER TABLE driver_availability ENABLE ALWAYS TRIGGER trg_resolve_driver_availability_conflict;
ALTER FUNCTION resolve_driver_availability_conflict() SET search_path = public;

-- ════════════════════════════════════════════════════════════════════════
-- LEAST-PRIVILEGE GRANTS for app_user (mirrors the app_user setup you
-- already ran) — safe to re-run, covers the new tables too.
-- ════════════════════════════════════════════════════════════════════════

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;

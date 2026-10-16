-- V8 (Depot): delivery points. See non-depot/V8 for the full rationale.
--
-- Towns such as Maseru and Butha-Buthe are delivery points INSIDE the
-- Destination site. delivery_point is a MASTER fragment written only here
-- at Depot and replicated to the other three sites.
--
-- ORDER MATTERS: apply non-depot/V8 at Border, Port and Destination FIRST,
-- then this file here. Logical replication does not replicate DDL, so the
-- other sites need the new columns before Depot sends rows that use them.
--
-- Then run infra/k8s/extend-replication-v8.sh to publish delivery_point,
-- and only after that run V9 to backfill existing rows.
--
-- The new columns are nullable: rows created before V8 have no delivery
-- point until V9. The application requires one on every new trip and
-- consignment.

CREATE TABLE delivery_point (
    delivery_point_id  UUID PRIMARY KEY,
    name               TEXT UNIQUE NOT NULL
);

ALTER TABLE trip_master  ADD COLUMN delivery_point_id UUID;
ALTER TABLE consignment  ADD COLUMN delivery_point_id UUID;

ALTER TABLE trip_master
    ADD CONSTRAINT trip_master_delivery_point_id_fkey
    FOREIGN KEY (delivery_point_id) REFERENCES delivery_point (delivery_point_id);

ALTER TABLE consignment
    ADD CONSTRAINT consignment_delivery_point_id_fkey
    FOREIGN KEY (delivery_point_id) REFERENCES delivery_point (delivery_point_id);

-- Snapshot grants from V2 do not cover new tables (bug #11 pattern).
-- Master fragments are immutable once created: create and delete only,
-- no UPDATE, matching the application's no-generic-edit decision.
GRANT SELECT, INSERT, DELETE ON delivery_point TO app_user;

-- Seed: the ten district capitals of Lesotho. Seeded here, at Depot only;
-- the other sites receive these rows by replication.
INSERT INTO delivery_point (delivery_point_id, name) VALUES
    (gen_random_uuid(), 'Maseru'),
    (gen_random_uuid(), 'Butha-Buthe'),
    (gen_random_uuid(), 'Hlotse'),
    (gen_random_uuid(), 'Teyateyaneng'),
    (gen_random_uuid(), 'Mafeteng'),
    (gen_random_uuid(), 'Mohale''s Hoek'),
    (gen_random_uuid(), 'Quthing'),
    (gen_random_uuid(), 'Qacha''s Nek'),
    (gen_random_uuid(), 'Mokhotlong'),
    (gen_random_uuid(), 'Thaba-Tseka');

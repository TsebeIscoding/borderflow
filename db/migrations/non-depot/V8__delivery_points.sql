-- V8 (Border, Port, Destination): delivery points.
--
-- Towns such as Maseru and Butha-Buthe are delivery points INSIDE the
-- Destination site, not separate sites -- the corridor stays
-- Depot -> Border -> Port -> Destination. A delivery point says where in
-- the Destination area a trip or consignment is going.
--
-- delivery_point is a MASTER fragment: written only at Depot, replicated
-- read-only everywhere else (same pattern as container_master, consignment).
-- This file only creates the empty table and the new columns; the rows
-- arrive by replication once extend-replication-v8.sh has run. Do NOT seed
-- rows here.
--
-- ORDER MATTERS: apply this file at Border, Port and Destination BEFORE
-- applying depot/V8 at Depot. Logical replication does not replicate DDL,
-- and once Depot sends rows carrying delivery_point_id, a site without the
-- column fails to apply them and its subscription stalls.
--
-- The new columns are nullable: rows created before V8 have no delivery
-- point until depot/V9 backfills them. The application requires one on
-- every new trip and consignment.

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

-- V2's GRANT ... ON ALL TABLES is a snapshot and does not cover tables
-- created later (the same trap as bug #11 with app_users). Read-only here:
-- master fragments are written at Depot only.
GRANT SELECT ON delivery_point TO app_user;
REVOKE INSERT, UPDATE, DELETE ON delivery_point FROM app_user;

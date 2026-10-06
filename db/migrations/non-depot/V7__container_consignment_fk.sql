-- V7: Same foreign key as depot/V7 (container_master.consignment_id ->
-- consignment), applied at every non-depot site for schema parity. V4
-- declared container_master before consignment and left the column as a
-- bare UUID NOT NULL, although the design doc specifies an FK.
--
-- Logical replication applies incoming rows without firing foreign-key
-- checks, so this does not make row replication order-sensitive here; the
-- constraint is effectively enforced at Depot, the only site that writes
-- these tables.
--
-- Fails if any existing container_master row references a missing
-- consignment -- fix that data first, then apply.

ALTER TABLE container_master
    ADD CONSTRAINT container_master_consignment_id_fkey
    FOREIGN KEY (consignment_id) REFERENCES consignment (consignment_id);

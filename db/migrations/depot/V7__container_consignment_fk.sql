-- V7: Adds the foreign key from container_master to consignment that the
-- design doc specifies (Container_Master.consignment_id FK) but V4 never
-- created -- container_master is declared before consignment in V4, so the
-- column was left as a bare UUID NOT NULL.
--
-- Without it, a Consignment could be deleted while Containers still
-- referenced it, leaving dangling Containers; and a Container could be
-- created pointing at a Consignment that does not exist.
--
-- Fails if any existing container_master row already references a missing
-- consignment -- fix that data first (restore the consignment or remove the
-- container), then apply.

ALTER TABLE container_master
    ADD CONSTRAINT container_master_consignment_id_fkey
    FOREIGN KEY (consignment_id) REFERENCES consignment (consignment_id);

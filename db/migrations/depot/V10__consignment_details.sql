-- V10 (Depot): consignment details. See non-depot/V10 for the full rationale.
--
-- ORDER MATTERS: apply non-depot/V10 at Border, Port and Destination FIRST,
-- then this file here (DDL does not replicate, and Depot must not send rows
-- with columns the other sites lack).
--
-- Then run V11 to give pre-V10 consignments a reference number.
--
-- Every new column is nullable because consignments created before V10 have
-- none of these details. The application requires cargo type, weight and
-- package count on every new consignment.

ALTER TABLE consignment
    ADD COLUMN reference_number        TEXT,
    ADD COLUMN cargo_type              TEXT,
    ADD COLUMN total_weight_kg         NUMERIC(10, 2),
    ADD COLUMN package_count           INTEGER,
    ADD COLUMN declared_value          NUMERIC(14, 2),
    ADD COLUMN currency                TEXT,
    ADD COLUMN customs_reference       TEXT,
    ADD COLUMN handling_notes          TEXT,
    ADD COLUMN delivery_location       TEXT,
    ADD COLUMN expected_delivery_date  DATE;

-- A human-readable reference (CON-2026-0001) that people can quote instead
-- of a UUID. NULLs are allowed by UNIQUE, so rows created before V10 are
-- fine until V11 backfills them.
ALTER TABLE consignment
    ADD CONSTRAINT consignment_reference_number_key UNIQUE (reference_number);

-- These CHECKs are also enforced when replicated rows are applied, so every
-- site must carry exactly the same definitions: change them only with a
-- migration applied at all four sites. NULL passes every CHECK, which keeps
-- pre-V10 rows valid.
ALTER TABLE consignment
    ADD CONSTRAINT consignment_cargo_type_check
        CHECK (cargo_type IN ('General', 'Refrigerated', 'Hazardous', 'Bulk')),
    ADD CONSTRAINT consignment_total_weight_kg_check
        CHECK (total_weight_kg > 0),
    ADD CONSTRAINT consignment_package_count_check
        CHECK (package_count > 0),
    ADD CONSTRAINT consignment_declared_value_check
        CHECK (declared_value >= 0),
    ADD CONSTRAINT consignment_currency_check
        CHECK (currency ~ '^[A-Z]{3}$');

-- Reference numbers come from a sequence that exists at Depot only: Depot
-- is the only site that creates consignments, so numbers cannot collide.
-- Like every table created after V2, it needs an explicit grant (the
-- snapshot grants do not cover it -- the bug #11 trap).
CREATE SEQUENCE consignment_reference_seq START 1;
GRANT USAGE, SELECT ON SEQUENCE consignment_reference_seq TO app_user;

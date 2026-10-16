-- V10 (Border, Port, Destination): consignment details.
--
-- Until now a consignment was only a client, a description and a delivery
-- point. This adds the details freight work needs: a human-readable
-- reference, cargo type, weight, package count, declared value and
-- currency, customs reference, handling notes, a business delivery
-- location (free text, business addresses only -- no names, phone numbers
-- or home addresses; client_contact stays the only place personal data
-- could ever live, and it still has no code), and an expected delivery
-- date.
--
-- consignment is a MASTER fragment (written only at Depot, replicated
-- everywhere else), so these columns are set at creation and never edited.
-- The table is already published, so no replication change is needed:
-- the new columns replicate with it automatically.
--
-- ORDER MATTERS: apply this file at Border, Port and Destination BEFORE
-- applying depot/V10 at Depot. Logical replication does not replicate DDL,
-- and once Depot sends rows carrying the new columns, a site without them
-- fails to apply the rows and its subscription stalls.
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

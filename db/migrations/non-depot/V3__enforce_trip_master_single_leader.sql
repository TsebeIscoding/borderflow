-- V3: Enforce single-leader writes on trip_master. NON-DEPOT SITES ONLY —
-- this file has no Depot counterpart; Depot is the origin site and keeps
-- write access.
--
-- Found via testing: without this, a non-origin site could locally UPDATE
-- trip_master and the write would silently never replicate anywhere,
-- causing permanent, undetected divergence (no trigger/log catches it,
-- unlike trip_state's conflict-resolution trigger).

REVOKE INSERT, UPDATE, DELETE ON trip_master FROM app_user;

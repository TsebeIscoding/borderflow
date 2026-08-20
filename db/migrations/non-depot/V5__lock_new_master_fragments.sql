-- V5: Same single-leader enforcement pattern as V3, applied to the new
-- Master fragments introduced in V4. NON-DEPOT SITES ONLY.

REVOKE INSERT, UPDATE, DELETE ON container_master, vehicle_profile, driver_profile, client_core, consignment FROM app_user;

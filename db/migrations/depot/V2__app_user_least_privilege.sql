-- V2: Least-privilege application role.
-- Found via testing: the cluster only had superuser 'postgres' available,
-- meaning single-leader write restrictions on Master fragments could not
-- actually be enforced by the database. This creates the role that later
-- REVOKE migrations (V3+) are scoped to.

CREATE ROLE app_user LOGIN PASSWORD 'change_me_later';
GRANT USAGE ON SCHEMA public TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;

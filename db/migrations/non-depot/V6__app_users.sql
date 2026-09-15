-- V6: Site-local staff accounts. Deliberately NEVER added to any
-- publication in setup-replication-k8s.sh / extend-replication-k8s.sh
-- -- there is no "global user directory" by design, matching the same
-- offline-first premise as everything else in this schema. Each site
-- manages its own accounts independently.
--
-- Only OPERATOR accounts exist at non-Depot sites. AUDITOR (cross-site,
-- read-only) tokens are only ever issued at Depot -- see
-- backend/src/main/java/com/borderflow/auth/JwtService.java.

CREATE TABLE app_users (
    id             UUID PRIMARY KEY,
    username       TEXT UNIQUE NOT NULL,
    password_hash  TEXT NOT NULL,
    role           TEXT NOT NULL CHECK (role IN ('OPERATOR', 'AUDITOR'))
);

-- Demo account for local dev / running the test scripts against the
-- frontend login form. Username: operator1, password: ChangeMe123!
-- CHANGE OR REMOVE before any deployment beyond a local Minikube demo.
--
-- id is supplied explicitly (not DB-generated) to match the app-supplied
-- UUID pattern used everywhere else in this schema -- see AppUser's
-- @GeneratedValue in the backend, which assigns the UUID in Java before
-- insert. A row inserted directly via psql (as here) has to supply one
-- itself, since there's no DEFAULT on this column.
INSERT INTO app_users (id, username, password_hash, role) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'operator1',
    '$2b$10$L4r7FKHHY3cAvS7qLz6Q7.h4Rrzk8T.CBcJuceCxJoO81POFdv5/O',
    'OPERATOR'
);

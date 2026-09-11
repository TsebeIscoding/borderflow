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
INSERT INTO app_users (username, password_hash, role) VALUES (
    'operator1',
    '$2b$10$L4r7FKHHY3cAvS7qLz6Q7.h4Rrzk8T.CBcJuceCxJoO81POFdv5/O',
    'OPERATOR'
);

-- V6: Site-local staff accounts. Deliberately NEVER added to any
-- publication -- see non-depot/V6__app_users.sql for the full
-- rationale (identical table, this is the Depot variant).
--
-- Depot additionally seeds an AUDITOR account, since Depot is the only
-- site whose key is trusted to issue cross-site AUDITOR tokens -- see
-- backend/src/main/java/com/borderflow/auth/JwtService.java.

CREATE TABLE app_users (
    id             UUID PRIMARY KEY,
    username       TEXT UNIQUE NOT NULL,
    password_hash  TEXT NOT NULL,
    role           TEXT NOT NULL CHECK (role IN ('OPERATOR', 'AUDITOR'))
);

-- Demo accounts for local dev. CHANGE OR REMOVE before any deployment
-- beyond a local Minikube demo. Both use password: ChangeMe123!
--
-- id is supplied explicitly (not DB-generated) -- see the matching
-- comment in non-depot/V6__app_users.sql for why.
INSERT INTO app_users (id, username, password_hash, role) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'operator1',
    '$2b$10$L4r7FKHHY3cAvS7qLz6Q7.h4Rrzk8T.CBcJuceCxJoO81POFdv5/O',
    'OPERATOR'
);
INSERT INTO app_users (id, username, password_hash, role) VALUES (
    '22222222-2222-2222-2222-222222222222',
    'auditor1',
    '$2b$10$L4r7FKHHY3cAvS7qLz6Q7.h4Rrzk8T.CBcJuceCxJoO81POFdv5/O',
    'AUDITOR'
);

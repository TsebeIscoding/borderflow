package com.borderflow.auth;

/**
 * OPERATOR -- site-local operational staff. Can read the manifest and
 * initiate handovers, but only ever authenticated at the one site whose
 * key signed their token (see JwtService).
 *
 * AUDITOR -- cross-site, read-only. Tokens are only ever issued at
 * Depot (see AuthController), verifiable offline at any site via
 * Depot's public key, no live call back to Depot required per request.
 */
public enum Role {
    OPERATOR,
    AUDITOR
}

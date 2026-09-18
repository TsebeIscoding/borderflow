package com.borderflow.common;

/**
 * Thrown when a non-origin site attempts to create or delete a Master
 * fragment record. Master fragments are single-leader by design (see
 * docs/design/vertical-fragmentation-design.md) -- only Depot may
 * write them, and every non-Depot site's app_user role has already
 * had INSERT/UPDATE/DELETE revoked on these tables at the database
 * level (db/migrations/non-depot/V3, V5). This exception is the
 * clean, early check: it stops the request before it ever reaches the
 * database, so the caller gets a clear business-rule message instead
 * of a raw SQL permission error. The database-level REVOKE remains the
 * actual enforcement backstop, the same relationship as every other
 * business rule enforced twice in this codebase (see
 * HandoverService's class javadoc for the same pattern).
 */
public class OriginSiteOnlyException extends RuntimeException {
    public OriginSiteOnlyException(String entityName, String thisSiteId) {
        super(entityName + " can only be created or deleted at the origin site (depot), not '" + thisSiteId + "'");
    }
}

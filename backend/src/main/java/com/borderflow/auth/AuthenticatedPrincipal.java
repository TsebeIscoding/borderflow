package com.borderflow.auth;

/**
 * The result of successfully validating a token -- who they are, what
 * role they were granted, which site they're home to, and (critically)
 * whether the signature checked out against THIS site's own key
 * (site-local operational token) or against DEPOT's key (cross-site
 * token). JwtAuthenticationFilter uses `crossSite` to decide which
 * Spring Security authority to grant -- see its class javadoc for why
 * that check exists at all instead of just trusting the `role` claim.
 */
public record AuthenticatedPrincipal(
        String username,
        Role role,
        String homeSiteId,
        boolean crossSite
) {
}

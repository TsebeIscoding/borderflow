package com.borderflow.handover;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Incoming request body for POST /api/trips/{tripId}/handover.
 * `toSiteId` is the only thing the caller decides -- `fromSiteId` is
 * always this service instance's own SITE_ID (see HandoverService),
 * never taken from the request. That's deliberate: a site should only
 * ever be able to say "I am handing this off," never "site X handed
 * this off," since that would let a compromised or buggy client at one
 * site forge a handover event on another site's behalf.
 */
public record HandoverRequest(
        @NotBlank String toSiteId,
        @NotBlank String verifiedBy
) {
}

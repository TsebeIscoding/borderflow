package com.borderflow.trip;

import jakarta.validation.constraints.NotBlank;

/**
 * `originSiteId` is never accepted from the caller -- it's always this
 * instance's own site.id, same reasoning as HandoverRequest never
 * accepting fromSiteId. Since only Depot's app_user can actually write
 * trip_master (see OriginSiteOnlyException's javadoc), origin is
 * always "depot" in practice.
 */
public record TripCreateRequest(
        @NotBlank String destinationSiteId
) {
}

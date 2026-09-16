package com.borderflow.container;

import jakarta.validation.constraints.NotBlank;

/** Same role/rationale as HandoverRequest -- see its javadoc for why fromSiteId is never accepted from the caller. */
public record ContainerRelocationRequest(
        @NotBlank String toSiteId,
        @NotBlank String verifiedBy
) {
}

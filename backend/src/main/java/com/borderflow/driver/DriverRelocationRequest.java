package com.borderflow.driver;

import jakarta.validation.constraints.NotBlank;

public record DriverRelocationRequest(
        @NotBlank String toSiteId,
        @NotBlank String verifiedBy
) {
}

package com.borderflow.vehicle;

import jakarta.validation.constraints.NotBlank;

public record VehicleRelocationRequest(
        @NotBlank String toSiteId,
        @NotBlank String verifiedBy
) {
}

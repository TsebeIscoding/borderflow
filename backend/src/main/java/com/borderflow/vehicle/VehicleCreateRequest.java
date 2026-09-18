package com.borderflow.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record VehicleCreateRequest(
        @NotBlank String registrationNumber,
        @NotNull @Positive BigDecimal capacity
) {
}

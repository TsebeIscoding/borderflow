package com.borderflow.driver;

import jakarta.validation.constraints.NotBlank;

public record DriverCreateRequest(
        @NotBlank String name,
        @NotBlank String licenseNumber,
        String phone
) {
}

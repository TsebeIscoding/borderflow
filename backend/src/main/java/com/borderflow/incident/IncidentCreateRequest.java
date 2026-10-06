package com.borderflow.incident;

import jakarta.validation.constraints.NotBlank;

public record IncidentCreateRequest(
        @NotBlank String description
) {
}

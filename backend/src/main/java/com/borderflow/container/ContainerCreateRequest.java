package com.borderflow.container;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ContainerCreateRequest(
        @NotBlank String containerNumber,
        @NotNull UUID consignmentId,
        @NotBlank String size
) {
}

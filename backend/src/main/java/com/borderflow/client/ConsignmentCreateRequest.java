package com.borderflow.client;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConsignmentCreateRequest(
        @NotNull UUID clientId,
        String description
) {
}

package com.borderflow.client;

import jakarta.validation.constraints.NotBlank;

public record ClientCreateRequest(
        @NotBlank String name
) {
}

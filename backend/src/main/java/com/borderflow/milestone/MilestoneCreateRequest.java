package com.borderflow.milestone;

import jakarta.validation.constraints.NotBlank;

public record MilestoneCreateRequest(
        @NotBlank String milestoneType
) {
}

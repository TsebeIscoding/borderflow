package com.borderflow.deliverypoint;

import jakarta.validation.constraints.NotBlank;

public record DeliveryPointCreateRequest(
        @NotBlank String name
) {
}

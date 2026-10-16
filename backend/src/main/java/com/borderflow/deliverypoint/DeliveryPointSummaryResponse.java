package com.borderflow.deliverypoint;

import java.util.UUID;

public record DeliveryPointSummaryResponse(
        UUID deliveryPointId,
        String name
) {
    static DeliveryPointSummaryResponse from(DeliveryPoint point) {
        return new DeliveryPointSummaryResponse(point.getDeliveryPointId(), point.getName());
    }
}

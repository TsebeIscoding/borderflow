package com.borderflow.client;

import java.util.UUID;

public record ConsignmentSummaryResponse(
        UUID consignmentId,
        UUID clientId,
        String description,
        UUID deliveryPointId
) {
    static ConsignmentSummaryResponse from(Consignment consignment) {
        return new ConsignmentSummaryResponse(
                consignment.getConsignmentId(),
                consignment.getClientId(),
                consignment.getDescription(),
                consignment.getDeliveryPointId()
        );
    }
}

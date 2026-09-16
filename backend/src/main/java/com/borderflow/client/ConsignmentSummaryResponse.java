package com.borderflow.client;

import java.util.UUID;

public record ConsignmentSummaryResponse(
        UUID consignmentId,
        UUID clientId,
        String description
) {
    static ConsignmentSummaryResponse from(Consignment consignment) {
        return new ConsignmentSummaryResponse(
                consignment.getConsignmentId(),
                consignment.getClientId(),
                consignment.getDescription()
        );
    }
}

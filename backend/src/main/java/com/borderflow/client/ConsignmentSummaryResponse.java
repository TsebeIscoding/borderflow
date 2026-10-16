package com.borderflow.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConsignmentSummaryResponse(
        UUID consignmentId,
        UUID clientId,
        String description,
        UUID deliveryPointId,
        String referenceNumber,
        String cargoType,
        BigDecimal totalWeightKg,
        Integer packageCount,
        BigDecimal declaredValue,
        String currency,
        String customsReference,
        String handlingNotes,
        String deliveryLocation,
        LocalDate expectedDeliveryDate
) {
    static ConsignmentSummaryResponse from(Consignment consignment) {
        return new ConsignmentSummaryResponse(
                consignment.getConsignmentId(),
                consignment.getClientId(),
                consignment.getDescription(),
                consignment.getDeliveryPointId(),
                consignment.getReferenceNumber(),
                consignment.getCargoType(),
                consignment.getTotalWeightKg(),
                consignment.getPackageCount(),
                consignment.getDeclaredValue(),
                consignment.getCurrency(),
                consignment.getCustomsReference(),
                consignment.getHandlingNotes(),
                consignment.getDeliveryLocation(),
                consignment.getExpectedDeliveryDate()
        );
    }
}

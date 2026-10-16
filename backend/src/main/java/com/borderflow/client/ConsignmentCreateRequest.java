package com.borderflow.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The reference number is never accepted from the caller -- it is always
 * issued from the Depot sequence. Declared value and currency go together
 * (checked in ConsignmentCreationService). deliveryLocation is a business
 * address as free text; the form tells users not to enter personal details.
 */
public record ConsignmentCreateRequest(
        @NotNull UUID clientId,
        String description,
        @NotNull UUID deliveryPointId,
        @NotBlank @Pattern(regexp = "General|Refrigerated|Hazardous|Bulk", message = "must be one of General, Refrigerated, Hazardous, Bulk") String cargoType,
        @NotNull @Positive BigDecimal totalWeightKg,
        @NotNull @Positive Integer packageCount,
        @PositiveOrZero BigDecimal declaredValue,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter code such as LSL") String currency,
        String customsReference,
        String handlingNotes,
        String deliveryLocation,
        LocalDate expectedDeliveryDate
) {
}

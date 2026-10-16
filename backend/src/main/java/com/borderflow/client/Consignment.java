package com.borderflow.client;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Static Master data, same as ClientCore -- no State fragment, no movement
 * concept. Every detail is set at creation and never edited (the project's
 * no-generic-update decision). The detail columns are nullable only because
 * consignments created before V10 have none of them; the application
 * requires cargo type, weight and package count on every new consignment.
 * The delivery location is a business address as free text -- no names,
 * phone numbers or home addresses are ever stored here.
 */
@Entity
@Table(name = "consignment")
public class Consignment {

    @Id
    @Column(name = "consignment_id")
    private UUID consignmentId;

    @Column(name = "client_id")
    private UUID clientId;

    private String description;

    @Column(name = "delivery_point_id")
    private UUID deliveryPointId;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "cargo_type")
    private String cargoType;

    @Column(name = "total_weight_kg")
    private BigDecimal totalWeightKg;

    @Column(name = "package_count")
    private Integer packageCount;

    @Column(name = "declared_value")
    private BigDecimal declaredValue;

    private String currency;

    @Column(name = "customs_reference")
    private String customsReference;

    @Column(name = "handling_notes")
    private String handlingNotes;

    @Column(name = "delivery_location")
    private String deliveryLocation;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    protected Consignment() {
        // JPA
    }

    public Consignment(
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
        this.consignmentId = consignmentId;
        this.clientId = clientId;
        this.description = description;
        this.deliveryPointId = deliveryPointId;
        this.referenceNumber = referenceNumber;
        this.cargoType = cargoType;
        this.totalWeightKg = totalWeightKg;
        this.packageCount = packageCount;
        this.declaredValue = declaredValue;
        this.currency = currency;
        this.customsReference = customsReference;
        this.handlingNotes = handlingNotes;
        this.deliveryLocation = deliveryLocation;
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public UUID getConsignmentId() {
        return consignmentId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getDescription() {
        return description;
    }

    /** Null only for consignments created before V8 and not yet backfilled by V9. */
    public UUID getDeliveryPointId() {
        return deliveryPointId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getCargoType() {
        return cargoType;
    }

    public BigDecimal getTotalWeightKg() {
        return totalWeightKg;
    }

    public Integer getPackageCount() {
        return packageCount;
    }

    public BigDecimal getDeclaredValue() {
        return declaredValue;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCustomsReference() {
        return customsReference;
    }

    public String getHandlingNotes() {
        return handlingNotes;
    }

    public String getDeliveryLocation() {
        return deliveryLocation;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }
}

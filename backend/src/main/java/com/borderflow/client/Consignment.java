package com.borderflow.client;

import jakarta.persistence.*;
import java.util.UUID;

/** Static Master data, same as ClientCore -- no State fragment, no movement concept. */
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

    protected Consignment() {
        // JPA
    }

    public Consignment(UUID consignmentId, UUID clientId, String description, UUID deliveryPointId) {
        this.consignmentId = consignmentId;
        this.clientId = clientId;
        this.description = description;
        this.deliveryPointId = deliveryPointId;
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
}

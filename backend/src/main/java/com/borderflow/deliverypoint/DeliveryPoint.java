package com.borderflow.deliverypoint;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Maps to `delivery_point` -- static Master data, single-leader like every
 * other Master fragment. A delivery point is a town inside the Destination
 * site's area (Maseru, Butha-Buthe and so on), not a separate site: the
 * corridor stays Depot, Border, Port, Destination. Written only at Depot
 * and replicated read-only everywhere else (see db/migrations V8).
 */
@Entity
@Table(name = "delivery_point")
public class DeliveryPoint {

    @Id
    @Column(name = "delivery_point_id")
    private UUID deliveryPointId;

    private String name;

    protected DeliveryPoint() {
        // JPA
    }

    public DeliveryPoint(UUID deliveryPointId, String name) {
        this.deliveryPointId = deliveryPointId;
        this.name = name;
    }

    public UUID getDeliveryPointId() {
        return deliveryPointId;
    }

    public String getName() {
        return name;
    }
}

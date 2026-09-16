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

    protected Consignment() {
        // JPA
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
}

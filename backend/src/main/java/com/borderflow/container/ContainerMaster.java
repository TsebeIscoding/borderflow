package com.borderflow.container;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Maps to `container_master` -- the Master fragment (see
 * the V4 migration under db/migrations). Single-leader: only ever written at Depot.
 * Locked down identically to trip_master -- non-origin sites have had
 * INSERT/UPDATE/DELETE revoked on this table for the `app_user` role
 * (db/migrations/non-depot/V5). No setter/mutator here on purpose --
 * see TripMaster's javadoc for the same reasoning.
 */
@Entity
@Table(name = "container_master")
public class ContainerMaster {

    @Id
    @Column(name = "container_id")
    private UUID containerId;

    @Column(name = "container_number")
    private String containerNumber;

    @Column(name = "consignment_id")
    private UUID consignmentId;

    private String size;

    protected ContainerMaster() {
        // JPA
    }

    public ContainerMaster(UUID containerId, String containerNumber, UUID consignmentId, String size) {
        this.containerId = containerId;
        this.containerNumber = containerNumber;
        this.consignmentId = consignmentId;
        this.size = size;
    }

    public UUID getContainerId() {
        return containerId;
    }

    public String getContainerNumber() {
        return containerNumber;
    }

    public UUID getConsignmentId() {
        return consignmentId;
    }

    public String getSize() {
        return size;
    }
}

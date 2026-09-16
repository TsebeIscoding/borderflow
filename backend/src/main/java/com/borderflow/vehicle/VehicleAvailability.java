package com.borderflow.vehicle;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * State fragment. Multi-leader, conflict-resolved by
 * resolve_vehicle_availability_conflict (identical Lamport rule as
 * every other State fragment). Unlike Container/Trip, "status" here
 * defaults to 'Available' and this codebase does not model
 * Available/Unavailable transitions -- relocate() only moves the
 * vehicle's current_site_id, it never changes status. Assign/release
 * semantics aren't built.
 */
@Entity
@Table(name = "vehicle_availability")
public class VehicleAvailability {

    @Id
    @Column(name = "vehicle_id")
    private UUID vehicleId;

    private String status;

    @Column(name = "current_site_id")
    private String currentSiteId;

    @Column(name = "lamport_ts")
    private long lamportTs;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected VehicleAvailability() {
        // JPA
    }

    public VehicleAvailability(UUID vehicleId, String status, String currentSiteId, long lamportTs) {
        this.vehicleId = vehicleId;
        this.status = status;
        this.currentSiteId = currentSiteId;
        this.lamportTs = lamportTs;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrentSiteId() {
        return currentSiteId;
    }

    public long getLamportTs() {
        return lamportTs;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void relocateTo(String newSiteId, long newLamportTs) {
        this.currentSiteId = newSiteId;
        this.lamportTs = newLamportTs;
        this.updatedAt = OffsetDateTime.now();
    }
}

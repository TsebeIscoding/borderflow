package com.borderflow.trip;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to the `trip_state` table -- the State fragment. Multi-leader:
 * writable at whichever site currently holds the trip. Conflict
 * resolution (Lamport comparison) is enforced by a Postgres trigger, not
 * application code -- see the V1 migration under db/migrations (both the
 * depot and non-depot copies). This entity only ever needs to set values
 * honestly; it does not need to defend against a stale write winning,
 * the database already does that.
 */
@Entity
@Table(name = "trip_state")
public class TripState {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    private String status;

    @Column(name = "current_site_id")
    private String currentSiteId;

    @Column(name = "lamport_ts")
    private long lamportTs;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** The trip's current vehicle and driver -- volatile, so part of the State fragment (V12). Null until assigned. */
    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    protected TripState() {
        // JPA
    }

    public TripState(UUID tripId, String status, String currentSiteId, long lamportTs) {
        this.tripId = tripId;
        this.status = status;
        this.currentSiteId = currentSiteId;
        this.lamportTs = lamportTs;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getTripId() {
        return tripId;
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

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    /**
     * Sets or clears the current vehicle (null clears it). Like advance(),
     * the caller chooses the next lamport_ts; the Postgres trigger decides
     * whether the write wins against a concurrent one.
     */
    public void assignVehicle(UUID newVehicleId, long newLamportTs) {
        this.vehicleId = newVehicleId;
        this.lamportTs = newLamportTs;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Sets or clears the current driver (null clears it). See assignVehicle. */
    public void assignDriver(UUID newDriverId, long newLamportTs) {
        this.driverId = newDriverId;
        this.lamportTs = newLamportTs;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Advances this trip to a new site/status. The caller (HandoverService)
     * is responsible for choosing the next lamport_ts -- this method just
     * applies it. The actual conflict rule (reject if the incoming
     * lamport_ts isn't strictly newer) lives in the Postgres trigger, on
     * purpose: it must hold even for writes this application didn't make,
     * e.g. ones arriving via replication from another site.
     */
    public void advance(String newSiteId, String newStatus, long newLamportTs) {
        this.currentSiteId = newSiteId;
        this.status = newStatus;
        this.lamportTs = newLamportTs;
        this.updatedAt = OffsetDateTime.now();
    }
}

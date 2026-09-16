package com.borderflow.driver;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Same shape as VehicleAvailability -- see its class javadoc for the design gap that carries over here too. */
@Entity
@Table(name = "driver_availability")
public class DriverAvailability {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    private String status;

    @Column(name = "current_site_id")
    private String currentSiteId;

    @Column(name = "lamport_ts")
    private long lamportTs;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected DriverAvailability() {
        // JPA
    }

    public DriverAvailability(UUID driverId, String status, String currentSiteId, long lamportTs) {
        this.driverId = driverId;
        this.status = status;
        this.currentSiteId = currentSiteId;
        this.lamportTs = lamportTs;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getDriverId() {
        return driverId;
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

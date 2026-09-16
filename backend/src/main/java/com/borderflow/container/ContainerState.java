package com.borderflow.container;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps to `container_state` -- the State fragment. Multi-leader,
 * conflict-resolved by `resolve_container_state_conflict` (identical
 * Lamport comparison rule as trip_state -- see the V4 migration under db/migrations).
 * Mirrors TripState's `advance()` pattern: this class only ever sets
 * values honestly, the database's trigger is what actually enforces
 * that a stale write can't win.
 */
@Entity
@Table(name = "container_state")
public class ContainerState {

    @Id
    @Column(name = "container_id")
    private UUID containerId;

    private String status;

    @Column(name = "current_site_id")
    private String currentSiteId;

    @Column(name = "last_milestone_id")
    private UUID lastMilestoneId;

    @Column(name = "lamport_ts")
    private long lamportTs;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by_site")
    private String updatedBySite;

    protected ContainerState() {
        // JPA
    }

    public ContainerState(UUID containerId, String status, String currentSiteId, long lamportTs) {
        this.containerId = containerId;
        this.status = status;
        this.currentSiteId = currentSiteId;
        this.lamportTs = lamportTs;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBySite = currentSiteId;
    }

    public UUID getContainerId() {
        return containerId;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrentSiteId() {
        return currentSiteId;
    }

    public UUID getLastMilestoneId() {
        return lastMilestoneId;
    }

    public long getLamportTs() {
        return lamportTs;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBySite() {
        return updatedBySite;
    }

    /** Same role as TripState.advance() -- see its javadoc for why the conflict rule lives in the database, not here. */
    public void advance(String newSiteId, String newStatus, long newLamportTs) {
        this.currentSiteId = newSiteId;
        this.status = newStatus;
        this.lamportTs = newLamportTs;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBySite = newSiteId;
    }
}

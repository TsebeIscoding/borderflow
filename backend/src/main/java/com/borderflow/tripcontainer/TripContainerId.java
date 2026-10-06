package com.borderflow.tripcontainer;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for `trip_container` -- required by JPA's @IdClass
 * mechanism since the table's primary key is (trip_id, container_id)
 * rather than a single generated column, unlike every other entity in
 * this codebase. Must implement equals/hashCode correctly or
 * Hibernate's identity checks silently misbehave.
 */
public class TripContainerId implements Serializable {

    private UUID tripId;
    private UUID containerId;

    public TripContainerId() {
        // JPA
    }

    public TripContainerId(UUID tripId, UUID containerId) {
        this.tripId = tripId;
        this.containerId = containerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TripContainerId that)) return false;
        return Objects.equals(tripId, that.tripId) && Objects.equals(containerId, that.containerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId, containerId);
    }
}

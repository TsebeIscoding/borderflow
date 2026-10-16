package com.borderflow.tripcontainer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface TripContainerRepository extends JpaRepository<TripContainer, TripContainerId> {
    List<TripContainer> findByTripId(UUID tripId);
    List<TripContainer> findByContainerId(UUID containerId);

    /** True if the container is linked to some trip other than this one that has not been delivered yet. */
    @Query("select count(tc) > 0 from TripContainer tc, TripState ts "
            + "where tc.containerId = :containerId and tc.tripId <> :tripId "
            + "and ts.tripId = tc.tripId and ts.status <> 'Delivered'")
    boolean existsOnOtherActiveTrip(@Param("containerId") UUID containerId, @Param("tripId") UUID tripId);
}

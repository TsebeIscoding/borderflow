package com.borderflow.tripcontainer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TripContainerRepository extends JpaRepository<TripContainer, TripContainerId> {
    List<TripContainer> findByTripId(UUID tripId);
    List<TripContainer> findByContainerId(UUID containerId);
}

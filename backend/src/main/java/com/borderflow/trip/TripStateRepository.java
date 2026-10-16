package com.borderflow.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TripStateRepository extends JpaRepository<TripState, UUID> {

    /** True if the vehicle is on any trip other than this one that has not been delivered yet. */
    boolean existsByVehicleIdAndTripIdNotAndStatusNot(UUID vehicleId, UUID tripId, String status);

    /** True if the driver is on any trip other than this one that has not been delivered yet. */
    boolean existsByDriverIdAndTripIdNotAndStatusNot(UUID driverId, UUID tripId, String status);
}

package com.borderflow.tripassignment;

import com.borderflow.common.DriverNotFoundException;
import com.borderflow.common.InvalidTripAssignmentException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.common.VehicleNotFoundException;
import com.borderflow.driver.DriverAvailabilityRepository;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import com.borderflow.vehicle.VehicleAvailabilityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Assigns and releases a trip's current vehicle and driver. The current
 * assignment lives in trip_state (volatile, resolved by Lamport timestamp
 * like position and status); every change also appends a row to the
 * trip_assignment history log.
 *
 * Rules, all checked here at the site making the change:
 * - the site must hold the trip (same "must hold" reasoning as handover) and
 *   the trip must not be Delivered
 * - assigning also requires the site to hold the vehicle or driver
 * - a vehicle or driver can be on only one active (not yet Delivered) trip
 *   at a time. This is deliberately NOT a unique index: on a replicated
 *   table, two sites assigning the same driver at once would make one of
 *   them fail while being applied, which halts replication. The "must
 *   hold" rule is what keeps concurrent assignment from happening in
 *   normal operation.
 * - assigning a different vehicle or driver releases the previous one first
 *   (two history rows); assigning the one already assigned is a no-op
 * - releasing when nothing is assigned is a no-op
 */
@Service
public class TripAssemblyService {

    private static final String VEHICLE = "VEHICLE";
    private static final String DRIVER = "DRIVER";

    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;
    private final VehicleAvailabilityRepository vehicleAvailabilityRepository;
    private final DriverAvailabilityRepository driverAvailabilityRepository;
    private final TripAssignmentRepository tripAssignmentRepository;
    private final String thisSiteId;

    public TripAssemblyService(
            TripMasterRepository tripMasterRepository,
            TripStateRepository tripStateRepository,
            VehicleAvailabilityRepository vehicleAvailabilityRepository,
            DriverAvailabilityRepository driverAvailabilityRepository,
            TripAssignmentRepository tripAssignmentRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
        this.vehicleAvailabilityRepository = vehicleAvailabilityRepository;
        this.driverAvailabilityRepository = driverAvailabilityRepository;
        this.tripAssignmentRepository = tripAssignmentRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public void assignVehicle(UUID tripId, UUID vehicleId) {
        TripState state = requireActiveTripHeldHere(tripId);

        String vehicleSite = vehicleAvailabilityRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId))
                .getCurrentSiteId();
        requireResourceHeldHere("Vehicle", vehicleId, vehicleSite);

        if (vehicleId.equals(state.getVehicleId())) {
            return;
        }
        if (tripStateRepository.existsByVehicleIdAndTripIdNotAndStatusNot(vehicleId, tripId, "Delivered")) {
            throw new InvalidTripAssignmentException("Vehicle " + vehicleId + " is already assigned to another active trip");
        }

        if (state.getVehicleId() != null) {
            record(tripId, VEHICLE, state.getVehicleId(), "RELEASED");
        }
        record(tripId, VEHICLE, vehicleId, "ASSIGNED");
        state.assignVehicle(vehicleId, state.getLamportTs() + 1);
        tripStateRepository.save(state);
    }

    @Transactional
    public void releaseVehicle(UUID tripId) {
        TripState state = requireActiveTripHeldHere(tripId);
        if (state.getVehicleId() == null) {
            return;
        }
        record(tripId, VEHICLE, state.getVehicleId(), "RELEASED");
        state.assignVehicle(null, state.getLamportTs() + 1);
        tripStateRepository.save(state);
    }

    @Transactional
    public void assignDriver(UUID tripId, UUID driverId) {
        TripState state = requireActiveTripHeldHere(tripId);

        String driverSite = driverAvailabilityRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId))
                .getCurrentSiteId();
        requireResourceHeldHere("Driver", driverId, driverSite);

        if (driverId.equals(state.getDriverId())) {
            return;
        }
        if (tripStateRepository.existsByDriverIdAndTripIdNotAndStatusNot(driverId, tripId, "Delivered")) {
            throw new InvalidTripAssignmentException("Driver " + driverId + " is already assigned to another active trip");
        }

        if (state.getDriverId() != null) {
            record(tripId, DRIVER, state.getDriverId(), "RELEASED");
        }
        record(tripId, DRIVER, driverId, "ASSIGNED");
        state.assignDriver(driverId, state.getLamportTs() + 1);
        tripStateRepository.save(state);
    }

    @Transactional
    public void releaseDriver(UUID tripId) {
        TripState state = requireActiveTripHeldHere(tripId);
        if (state.getDriverId() == null) {
            return;
        }
        record(tripId, DRIVER, state.getDriverId(), "RELEASED");
        state.assignDriver(null, state.getLamportTs() + 1);
        tripStateRepository.save(state);
    }

    public List<TripAssignmentResponse> history(UUID tripId) {
        if (!tripMasterRepository.existsById(tripId)) {
            throw new TripNotFoundException(tripId);
        }
        return tripAssignmentRepository.findByTripIdOrderByOccurredAtAsc(tripId).stream()
                .map(TripAssignmentResponse::from)
                .toList();
    }

    private TripState requireActiveTripHeldHere(UUID tripId) {
        TripState state = tripStateRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        if (!thisSiteId.equals(state.getCurrentSiteId())) {
            throw new InvalidTripAssignmentException(
                    "Trip " + tripId + " is currently held at '" + state.getCurrentSiteId() + "', not '" + thisSiteId
                            + "' -- cannot change its vehicle or driver from a site that doesn't hold it");
        }
        if ("Delivered".equals(state.getStatus())) {
            throw new InvalidTripAssignmentException("Trip " + tripId + " is already Delivered -- its vehicle and driver can no longer change");
        }
        return state;
    }

    private void requireResourceHeldHere(String kind, UUID resourceId, String resourceSite) {
        if (!thisSiteId.equals(resourceSite)) {
            throw new InvalidTripAssignmentException(
                    kind + " " + resourceId + " is currently at '" + resourceSite + "', not '" + thisSiteId
                            + "' -- it can only be assigned from the site that holds it");
        }
    }

    private void record(UUID tripId, String resourceType, UUID resourceId, String action) {
        tripAssignmentRepository.save(new TripAssignment(UUID.randomUUID(), thisSiteId, tripId, resourceType, resourceId, action));
    }
}

package com.borderflow.handover;

import com.borderflow.common.DriverNotFoundException;
import com.borderflow.common.InvalidHandoverException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.common.VehicleNotFoundException;
import com.borderflow.container.ContainerState;
import com.borderflow.container.ContainerStateRepository;
import com.borderflow.driver.DriverAvailability;
import com.borderflow.driver.DriverAvailabilityRepository;
import com.borderflow.trip.TripMaster;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import com.borderflow.tripcontainer.TripContainer;
import com.borderflow.tripcontainer.TripContainerRepository;
import com.borderflow.vehicle.VehicleAvailability;
import com.borderflow.vehicle.VehicleAvailabilityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The core BorderFlow use case: one site handing custody of a trip to
 * another. Everything that isn't already enforced by the database lives
 * here -- see the class-level comments on TripState/TripMaster for what
 * IS left to the database (Lamport conflict resolution, event
 * idempotency, single-leader writes on Master fragments).
 *
 * Handing over a trip hands over the whole load: its linked containers and
 * its assigned vehicle and driver move to the receiving site in the same
 * transaction, arriving as "Arrived" exactly as a separate relocation would.
 * The sending site must hold ALL of them, otherwise the hand-off is refused
 * and nothing moves -- never half a trip.
 */
@Service
public class HandoverService {

    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;
    private final HandoverRepository handoverRepository;
    private final TripContainerRepository tripContainerRepository;
    private final ContainerStateRepository containerStateRepository;
    private final VehicleAvailabilityRepository vehicleAvailabilityRepository;
    private final DriverAvailabilityRepository driverAvailabilityRepository;

    /**
     * Which site THIS running instance represents. Injected from
     * application.yml / the SITE_ID env var -- see
     * infra/k8s/<site>/*.yaml for how each of the four deployments sets
     * this differently. Never taken from the request body (see
     * HandoverRequest's javadoc for why).
     */
    private final String thisSiteId;

    public HandoverService(
            TripMasterRepository tripMasterRepository,
            TripStateRepository tripStateRepository,
            HandoverRepository handoverRepository,
            TripContainerRepository tripContainerRepository,
            ContainerStateRepository containerStateRepository,
            VehicleAvailabilityRepository vehicleAvailabilityRepository,
            DriverAvailabilityRepository driverAvailabilityRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
        this.handoverRepository = handoverRepository;
        this.tripContainerRepository = tripContainerRepository;
        this.containerStateRepository = containerStateRepository;
        this.vehicleAvailabilityRepository = vehicleAvailabilityRepository;
        this.driverAvailabilityRepository = driverAvailabilityRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public HandoverResponse handOff(UUID tripId, HandoverRequest request) {
        TripMaster master = tripMasterRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        TripState state = tripStateRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));

        // Rule 1: a site can only hand off a trip it currently holds.
        // This is a same-site check the database can't express on its
        // own -- trip_state has no notion of "who is allowed to write
        // right now," only "which write wins if two happen concurrently."
        if (!thisSiteId.equals(state.getCurrentSiteId())) {
            throw new InvalidHandoverException(
                    "Trip " + tripId + " is currently held at '" + state.getCurrentSiteId() +
                            "', not '" + thisSiteId + "' -- cannot hand off a trip this site doesn't hold");
        }

        // Rule 2: a delivered trip is terminal. Re-running a handover
        // past Delivered would be a logic bug upstream, not a network
        // retry -- retries of the SAME handover are already handled by
        // the database's event-idempotency trigger (skip_duplicate_handover),
        // this check is specifically about accepting a NEW handover that
        // shouldn't exist at all.
        if ("Delivered".equals(state.getStatus())) {
            throw new InvalidHandoverException("Trip " + tripId + " is already Delivered -- no further handover is valid");
        }

        // Rule 3: the whole load moves with the trip, so this site must hold
        // all of it. Checked before anything is written, so a refusal leaves
        // everything exactly where it was.
        List<String> notHeldHere = new ArrayList<>();

        List<ContainerState> containers = new ArrayList<>();
        for (TripContainer link : tripContainerRepository.findByTripId(tripId)) {
            ContainerState container = containerStateRepository.findById(link.getContainerId()).orElse(null);
            if (container == null) {
                continue;
            }
            if (thisSiteId.equals(container.getCurrentSiteId())) {
                containers.add(container);
            } else {
                notHeldHere.add("container " + link.getContainerId() + " is at '" + container.getCurrentSiteId() + "'");
            }
        }

        VehicleAvailability vehicle = null;
        if (state.getVehicleId() != null) {
            vehicle = vehicleAvailabilityRepository.findById(state.getVehicleId())
                    .orElseThrow(() -> new VehicleNotFoundException(state.getVehicleId()));
            if (!thisSiteId.equals(vehicle.getCurrentSiteId())) {
                notHeldHere.add("vehicle " + state.getVehicleId() + " is at '" + vehicle.getCurrentSiteId() + "'");
            }
        }

        DriverAvailability driver = null;
        if (state.getDriverId() != null) {
            driver = driverAvailabilityRepository.findById(state.getDriverId())
                    .orElseThrow(() -> new DriverNotFoundException(state.getDriverId()));
            if (!thisSiteId.equals(driver.getCurrentSiteId())) {
                notHeldHere.add("driver " + state.getDriverId() + " is at '" + driver.getCurrentSiteId() + "'");
            }
        }

        if (!notHeldHere.isEmpty()) {
            throw new InvalidHandoverException(
                    "Trip " + tripId + " cannot be handed off from '" + thisSiteId + "' -- its load is not all held here: "
                            + String.join("; ", notHeldHere));
        }

        String newStatus = request.toSiteId().equals(master.getDestinationSiteId())
                ? "Delivered"
                : "Arrived";
        long newLamportTs = state.getLamportTs() + 1;

        Handover event = new Handover(
                UUID.randomUUID(),
                tripId,
                thisSiteId,
                request.toSiteId(),
                request.verifiedBy()
        );
        handoverRepository.save(event);

        state.advance(request.toSiteId(), newStatus, newLamportTs);
        tripStateRepository.save(state);

        // The load moves with the trip. Each lands on "Arrived", as a separate
        // relocation would (containers, vehicles and drivers have no Delivered
        // status; see ContainerRelocationService).
        for (ContainerState container : containers) {
            container.advance(request.toSiteId(), "Arrived", container.getLamportTs() + 1);
            containerStateRepository.save(container);
        }
        if (vehicle != null) {
            vehicle.relocateTo(request.toSiteId(), vehicle.getLamportTs() + 1);
            vehicleAvailabilityRepository.save(vehicle);
        }
        if (driver != null) {
            driver.relocateTo(request.toSiteId(), driver.getLamportTs() + 1);
            driverAvailabilityRepository.save(driver);
        }

        return new HandoverResponse(
                event.getEventId(),
                tripId,
                thisSiteId,
                request.toSiteId(),
                newStatus,
                newLamportTs,
                containers.size(),
                vehicle != null,
                driver != null
        );
    }
}

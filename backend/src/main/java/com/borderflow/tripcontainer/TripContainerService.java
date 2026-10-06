package com.borderflow.tripcontainer;

import com.borderflow.common.ContainerNotFoundException;
import com.borderflow.common.InvalidTripContainerLinkException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.container.ContainerMasterRepository;
import com.borderflow.container.ContainerStateRepository;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Links/unlinks a Container onto a Trip's manifest -- the
 * Trip_Container association the design calls for but which had no
 * code anywhere in this project until now. Business rule: a site can
 * only link (or unlink) a pair it currently holds BOTH halves of --
 * you'd physically add a container to a trip's manifest at whichever
 * site is handling both of them right now, the same "must hold"
 * reasoning as HandoverService and every *RelocationService, just
 * applied to two entities at once instead of one.
 */
@Service
public class TripContainerService {

    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;
    private final ContainerMasterRepository containerMasterRepository;
    private final ContainerStateRepository containerStateRepository;
    private final TripContainerRepository tripContainerRepository;
    private final String thisSiteId;

    public TripContainerService(
            TripMasterRepository tripMasterRepository,
            TripStateRepository tripStateRepository,
            ContainerMasterRepository containerMasterRepository,
            ContainerStateRepository containerStateRepository,
            TripContainerRepository tripContainerRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
        this.containerMasterRepository = containerMasterRepository;
        this.containerStateRepository = containerStateRepository;
        this.tripContainerRepository = tripContainerRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public void link(UUID tripId, UUID containerId) {
        requireBothHeldHere(tripId, containerId);
        // Idempotent by design -- the database's own dedup trigger
        // (skip_duplicate_trip_container) silently absorbs a repeat
        // link of the same pair, so no existence check is needed here.
        tripContainerRepository.save(new TripContainer(tripId, containerId, thisSiteId));
    }

    @Transactional
    public void unlink(UUID tripId, UUID containerId) {
        requireBothHeldHere(tripId, containerId);
        tripContainerRepository.deleteById(new TripContainerId(tripId, containerId));
    }

    public List<TripContainer> containersOnTrip(UUID tripId) {
        if (!tripMasterRepository.existsById(tripId)) {
            throw new TripNotFoundException(tripId);
        }
        return tripContainerRepository.findByTripId(tripId);
    }

    public List<TripContainer> tripsForContainer(UUID containerId) {
        if (!containerMasterRepository.existsById(containerId)) {
            throw new ContainerNotFoundException(containerId);
        }
        return tripContainerRepository.findByContainerId(containerId);
    }

    private void requireBothHeldHere(UUID tripId, UUID containerId) {
        String tripSite = tripStateRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId))
                .getCurrentSiteId();
        String containerSite = containerStateRepository.findById(containerId)
                .orElseThrow(() -> new ContainerNotFoundException(containerId))
                .getCurrentSiteId();

        if (!thisSiteId.equals(tripSite) || !thisSiteId.equals(containerSite)) {
            throw new InvalidTripContainerLinkException(
                    "Cannot link trip and container here -- trip is at '" + tripSite + "', container is at '" +
                            containerSite + "', this site is '" + thisSiteId + "'. Both must currently be at this site.");
        }
    }
}

package com.borderflow.container;

import com.borderflow.common.ContainerNotFoundException;
import com.borderflow.common.InvalidContainerMoveException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Container's equivalent of HandoverService, with one honest gap:
 * unlike Trip, `container_master` has no destination_site_id, so there
 * is no "Delivered" terminal status here -- every relocation lands on
 * "Arrived" regardless of which site receives it. A real implementation
 * would likely tie a container's terminal status to whichever Trip is
 * currently carrying it (via Trip_Container), not track it
 * independently -- that association isn't built yet.
 *
 * Also unlike Handover, there's no matching event-log insert here: the
 * `milestone` table (the V4 migration under db/migrations) was scoped to `trip_id`, not
 * `container_id`, when originally designed, so it doesn't cleanly fit
 * a container-only movement event. Documented here rather than forcing
 * a fix into the schema under time pressure.
 */
@Service
public class ContainerRelocationService {

    private final ContainerMasterRepository containerMasterRepository;
    private final ContainerStateRepository containerStateRepository;
    private final String thisSiteId;

    public ContainerRelocationService(
            ContainerMasterRepository containerMasterRepository,
            ContainerStateRepository containerStateRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.containerMasterRepository = containerMasterRepository;
        this.containerStateRepository = containerStateRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public ContainerRelocationResponse relocate(UUID containerId, ContainerRelocationRequest request) {
        containerMasterRepository.findById(containerId)
                .orElseThrow(() -> new ContainerNotFoundException(containerId));

        ContainerState state = containerStateRepository.findById(containerId)
                .orElseThrow(() -> new ContainerNotFoundException(containerId));

        // Same rule as HandoverService's Rule 1 -- see its javadoc.
        if (!thisSiteId.equals(state.getCurrentSiteId())) {
            throw new InvalidContainerMoveException(
                    "Container " + containerId + " is currently held at '" + state.getCurrentSiteId() +
                            "', not '" + thisSiteId + "' -- cannot relocate a container this site doesn't hold");
        }

        long newLamportTs = state.getLamportTs() + 1;
        state.advance(request.toSiteId(), "Arrived", newLamportTs);
        containerStateRepository.save(state);

        return new ContainerRelocationResponse(containerId, thisSiteId, request.toSiteId(), "Arrived", newLamportTs);
    }
}

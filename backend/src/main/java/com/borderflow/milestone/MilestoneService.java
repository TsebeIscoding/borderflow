package com.borderflow.milestone;

import com.borderflow.common.InvalidHandoverException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.trip.TripStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Records a milestone against a Trip. Same "must currently hold this
 * trip" rule as HandoverService -- a site records a milestone for a
 * trip it's actually handling right now, not one it read about from
 * replication. Reuses InvalidHandoverException rather than a new
 * Milestone-specific one, since it's the exact same rule (not a
 * design smell -- see HandoverService's class javadoc for what this
 * rule protects against).
 */
@Service
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final TripStateRepository tripStateRepository;
    private final String thisSiteId;

    public MilestoneService(
            MilestoneRepository milestoneRepository,
            TripStateRepository tripStateRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.milestoneRepository = milestoneRepository;
        this.tripStateRepository = tripStateRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public MilestoneResponse record(UUID tripId, MilestoneCreateRequest request) {
        String currentSite = tripStateRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId))
                .getCurrentSiteId();

        if (!thisSiteId.equals(currentSite)) {
            throw new InvalidHandoverException(
                    "Trip " + tripId + " is currently held at '" + currentSite +
                            "', not '" + thisSiteId + "' -- cannot record a milestone for a trip this site doesn't hold");
        }

        Milestone milestone = new Milestone(UUID.randomUUID(), thisSiteId, tripId, request.milestoneType());
        milestoneRepository.save(milestone);
        return MilestoneResponse.from(milestone);
    }

    public List<MilestoneResponse> forTrip(UUID tripId) {
        return milestoneRepository.findByTripIdOrderByOccurredAtAsc(tripId).stream()
                .map(MilestoneResponse::from)
                .toList();
    }
}

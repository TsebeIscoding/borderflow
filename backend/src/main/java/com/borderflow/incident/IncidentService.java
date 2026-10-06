package com.borderflow.incident;

import com.borderflow.common.InvalidHandoverException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.trip.TripStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** Same shape as MilestoneService -- see its class javadoc. */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final TripStateRepository tripStateRepository;
    private final String thisSiteId;

    public IncidentService(
            IncidentRepository incidentRepository,
            TripStateRepository tripStateRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.incidentRepository = incidentRepository;
        this.tripStateRepository = tripStateRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public IncidentResponse record(UUID tripId, IncidentCreateRequest request) {
        String currentSite = tripStateRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId))
                .getCurrentSiteId();

        if (!thisSiteId.equals(currentSite)) {
            throw new InvalidHandoverException(
                    "Trip " + tripId + " is currently held at '" + currentSite +
                            "', not '" + thisSiteId + "' -- cannot record an incident for a trip this site doesn't hold");
        }

        Incident incident = new Incident(UUID.randomUUID(), thisSiteId, tripId, request.description());
        incidentRepository.save(incident);
        return IncidentResponse.from(incident);
    }

    public List<IncidentResponse> forTrip(UUID tripId) {
        return incidentRepository.findByTripIdOrderByOccurredAtAsc(tripId).stream()
                .map(IncidentResponse::from)
                .toList();
    }
}

package com.borderflow.handover;

import com.borderflow.common.InvalidHandoverException;
import com.borderflow.common.TripNotFoundException;
import com.borderflow.trip.TripMaster;
import com.borderflow.trip.TripMasterRepository;
import com.borderflow.trip.TripState;
import com.borderflow.trip.TripStateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * The core BorderFlow use case: one site handing custody of a trip to
 * another. Everything that isn't already enforced by the database lives
 * here -- see the class-level comments on TripState/TripMaster for what
 * IS left to the database (Lamport conflict resolution, event
 * idempotency, single-leader writes on Master fragments).
 */
@Service
public class HandoverService {

    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;
    private final HandoverRepository handoverRepository;

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
            @Value("${site.id}") String thisSiteId
    ) {
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
        this.handoverRepository = handoverRepository;
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

        return new HandoverResponse(
                event.getEventId(),
                tripId,
                thisSiteId,
                request.toSiteId(),
                newStatus,
                newLamportTs
        );
    }
}

package com.borderflow.trip;

import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Create and delete for the Trip Master fragment. Both operations are
 * origin-only (see OriginSiteOnlyException's javadoc) -- unlike
 * HandoverService, there is no "Update" here: Master fields are
 * immutable by design once created (see
 * docs/design/vertical-fragmentation-design.md). The State fragment's
 * own "update" operation is HandoverService.handOff, which is a
 * distinct, already-existing use case, not something this class
 * duplicates.
 */
@Service
public class TripCreationService {

    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;
    private final String thisSiteId;

    public TripCreationService(
            TripMasterRepository tripMasterRepository,
            TripStateRepository tripStateRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public TripSummaryResponse create(TripCreateRequest request) {
        requireOriginSite();

        UUID tripId = UUID.randomUUID();
        TripMaster master = new TripMaster(tripId, thisSiteId, request.destinationSiteId());
        TripState state = new TripState(tripId, "AtOrigin", thisSiteId, 1);

        tripMasterRepository.save(master);
        tripStateRepository.save(state);

        return TripSummaryResponse.from(master, state);
    }

    @Transactional
    public void delete(UUID tripId) {
        requireOriginSite();
        try {
            // See ClientCreationService.delete's comment for why the
            // explicit flush() matters -- without it, a constraint
            // violation would surface at commit time, outside this catch.
            tripStateRepository.deleteById(tripId);
            tripStateRepository.flush();
            tripMasterRepository.deleteById(tripId);
            tripMasterRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EntityInUseException("Trip " + tripId + " cannot be deleted -- it still has related records (e.g. handover events) referencing it");
        }
    }

    private void requireOriginSite() {
        if (!"depot".equals(thisSiteId)) {
            throw new OriginSiteOnlyException("Trip", thisSiteId);
        }
    }
}

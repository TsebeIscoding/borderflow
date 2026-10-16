package com.borderflow.deliverypoint;

import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Create and delete for delivery points. Origin-only like every other
 * Master fragment. There is no update: Master data is immutable once
 * created. Deleting a delivery point that a trip or consignment still
 * references fails with EntityInUseException (a clean 409), via the
 * foreign keys added in V8.
 */
@Service
public class DeliveryPointService {

    private final DeliveryPointRepository deliveryPointRepository;
    private final String thisSiteId;

    public DeliveryPointService(DeliveryPointRepository deliveryPointRepository, @Value("${site.id}") String thisSiteId) {
        this.deliveryPointRepository = deliveryPointRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public DeliveryPointSummaryResponse create(DeliveryPointCreateRequest request) {
        requireOriginSite();
        DeliveryPoint point = new DeliveryPoint(UUID.randomUUID(), request.name().trim());
        deliveryPointRepository.save(point);
        return DeliveryPointSummaryResponse.from(point);
    }

    @Transactional
    public void delete(UUID deliveryPointId) {
        requireOriginSite();
        try {
            // Explicit flush so a foreign-key violation surfaces here, inside
            // the catch, not at commit time (see ClientCreationService.delete).
            deliveryPointRepository.deleteById(deliveryPointId);
            deliveryPointRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EntityInUseException("Delivery point " + deliveryPointId + " cannot be deleted -- trips or consignments still reference it");
        }
    }

    private void requireOriginSite() {
        if (!"depot".equals(thisSiteId)) {
            throw new OriginSiteOnlyException("Delivery point", thisSiteId);
        }
    }
}

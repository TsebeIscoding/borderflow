package com.borderflow.client;

import com.borderflow.common.ClientNotFoundException;
import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Same shape as ClientCreationService. Validates the referenced client
 * exists before insert -- the database's own foreign key would catch
 * a missing client anyway, but checking first gives a clean 404
 * instead of a raw constraint-violation 500.
 */
@Service
public class ConsignmentCreationService {

    private final ConsignmentRepository consignmentRepository;
    private final ClientCoreRepository clientCoreRepository;
    private final String thisSiteId;

    public ConsignmentCreationService(
            ConsignmentRepository consignmentRepository,
            ClientCoreRepository clientCoreRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.consignmentRepository = consignmentRepository;
        this.clientCoreRepository = clientCoreRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public ConsignmentSummaryResponse create(ConsignmentCreateRequest request) {
        requireOriginSite();

        if (!clientCoreRepository.existsById(request.clientId())) {
            throw new ClientNotFoundException(request.clientId());
        }

        Consignment consignment = new Consignment(UUID.randomUUID(), request.clientId(), request.description());
        consignmentRepository.save(consignment);
        return ConsignmentSummaryResponse.from(consignment);
    }

    @Transactional
    public void delete(UUID consignmentId) {
        requireOriginSite();
        try {
            // See ClientCreationService.delete's comment for why the
            // explicit flush() matters.
            consignmentRepository.deleteById(consignmentId);
            consignmentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EntityInUseException("Consignment " + consignmentId + " cannot be deleted -- it still has related records referencing it");
        }
    }

    private void requireOriginSite() {
        if (!"depot".equals(thisSiteId)) {
            throw new OriginSiteOnlyException("Consignment", thisSiteId);
        }
    }
}

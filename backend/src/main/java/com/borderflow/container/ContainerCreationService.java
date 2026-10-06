package com.borderflow.container;

import com.borderflow.client.ConsignmentRepository;
import com.borderflow.common.ConsignmentNotFoundException;
import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Same shape as TripCreationService -- see its class javadoc. Validates the
 * referenced consignment exists before insert, so a missing consignment
 * gives a clean 404 instead of a raw foreign-key violation (the database
 * enforces the same rule via container_master_consignment_id_fkey, V7).
 */
@Service
public class ContainerCreationService {

    private final ContainerMasterRepository containerMasterRepository;
    private final ContainerStateRepository containerStateRepository;
    private final ConsignmentRepository consignmentRepository;
    private final String thisSiteId;

    public ContainerCreationService(
            ContainerMasterRepository containerMasterRepository,
            ContainerStateRepository containerStateRepository,
            ConsignmentRepository consignmentRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.containerMasterRepository = containerMasterRepository;
        this.containerStateRepository = containerStateRepository;
        this.consignmentRepository = consignmentRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public ContainerSummaryResponse create(ContainerCreateRequest request) {
        requireOriginSite();

        if (!consignmentRepository.existsById(request.consignmentId())) {
            throw new ConsignmentNotFoundException(request.consignmentId());
        }

        UUID containerId = UUID.randomUUID();
        ContainerMaster master = new ContainerMaster(containerId, request.containerNumber(), request.consignmentId(), request.size());
        ContainerState state = new ContainerState(containerId, "AtOrigin", thisSiteId, 1);

        containerMasterRepository.save(master);
        containerStateRepository.save(state);

        return ContainerSummaryResponse.from(master, state);
    }

    @Transactional
    public void delete(UUID containerId) {
        requireOriginSite();
        try {
            // See ClientCreationService.delete's comment for why the
            // explicit flush() matters.
            containerStateRepository.deleteById(containerId);
            containerStateRepository.flush();
            containerMasterRepository.deleteById(containerId);
            containerMasterRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EntityInUseException("Container " + containerId + " cannot be deleted -- it still has related records referencing it");
        }
    }

    private void requireOriginSite() {
        if (!"depot".equals(thisSiteId)) {
            throw new OriginSiteOnlyException("Container", thisSiteId);
        }
    }
}

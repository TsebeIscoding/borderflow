package com.borderflow.container;

import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Same shape as TripCreationService -- see its class javadoc. */
@Service
public class ContainerCreationService {

    private final ContainerMasterRepository containerMasterRepository;
    private final ContainerStateRepository containerStateRepository;
    private final String thisSiteId;

    public ContainerCreationService(
            ContainerMasterRepository containerMasterRepository,
            ContainerStateRepository containerStateRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.containerMasterRepository = containerMasterRepository;
        this.containerStateRepository = containerStateRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public ContainerSummaryResponse create(ContainerCreateRequest request) {
        requireOriginSite();

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

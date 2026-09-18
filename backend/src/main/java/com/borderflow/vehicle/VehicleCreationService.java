package com.borderflow.vehicle;

import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Same shape as ContainerCreationService. */
@Service
public class VehicleCreationService {

    private final VehicleProfileRepository vehicleProfileRepository;
    private final VehicleAvailabilityRepository vehicleAvailabilityRepository;
    private final String thisSiteId;

    public VehicleCreationService(
            VehicleProfileRepository vehicleProfileRepository,
            VehicleAvailabilityRepository vehicleAvailabilityRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.vehicleProfileRepository = vehicleProfileRepository;
        this.vehicleAvailabilityRepository = vehicleAvailabilityRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public VehicleSummaryResponse create(VehicleCreateRequest request) {
        requireOriginSite();

        UUID vehicleId = UUID.randomUUID();
        VehicleProfile profile = new VehicleProfile(vehicleId, request.registrationNumber(), request.capacity());
        VehicleAvailability availability = new VehicleAvailability(vehicleId, "Available", thisSiteId, 1);

        vehicleProfileRepository.save(profile);
        vehicleAvailabilityRepository.save(availability);

        return VehicleSummaryResponse.from(profile, availability);
    }

    @Transactional
    public void delete(UUID vehicleId) {
        requireOriginSite();
        try {
            // See ClientCreationService.delete's comment for why the
            // explicit flush() matters.
            vehicleAvailabilityRepository.deleteById(vehicleId);
            vehicleAvailabilityRepository.flush();
            vehicleProfileRepository.deleteById(vehicleId);
            vehicleProfileRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EntityInUseException("Vehicle " + vehicleId + " cannot be deleted -- it still has related records referencing it");
        }
    }

    private void requireOriginSite() {
        if (!"depot".equals(thisSiteId)) {
            throw new OriginSiteOnlyException("Vehicle", thisSiteId);
        }
    }
}

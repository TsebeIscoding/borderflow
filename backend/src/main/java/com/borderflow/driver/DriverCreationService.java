package com.borderflow.driver;

import com.borderflow.common.EntityInUseException;
import com.borderflow.common.OriginSiteOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Same shape as VehicleCreationService. */
@Service
public class DriverCreationService {

    private final DriverProfileRepository driverProfileRepository;
    private final DriverAvailabilityRepository driverAvailabilityRepository;
    private final String thisSiteId;

    public DriverCreationService(
            DriverProfileRepository driverProfileRepository,
            DriverAvailabilityRepository driverAvailabilityRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.driverProfileRepository = driverProfileRepository;
        this.driverAvailabilityRepository = driverAvailabilityRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public DriverSummaryResponse create(DriverCreateRequest request) {
        requireOriginSite();

        UUID driverId = UUID.randomUUID();
        DriverProfile profile = new DriverProfile(driverId, request.name(), request.licenseNumber(), request.phone());
        DriverAvailability availability = new DriverAvailability(driverId, "Available", thisSiteId, 1);

        driverProfileRepository.save(profile);
        driverAvailabilityRepository.save(availability);

        return DriverSummaryResponse.from(profile, availability);
    }

    @Transactional
    public void delete(UUID driverId) {
        requireOriginSite();
        try {
            // See ClientCreationService.delete's comment for why the
            // explicit flush() matters.
            driverAvailabilityRepository.deleteById(driverId);
            driverAvailabilityRepository.flush();
            driverProfileRepository.deleteById(driverId);
            driverProfileRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EntityInUseException("Driver " + driverId + " cannot be deleted -- it still has related records referencing it");
        }
    }

    private void requireOriginSite() {
        if (!"depot".equals(thisSiteId)) {
            throw new OriginSiteOnlyException("Driver", thisSiteId);
        }
    }
}

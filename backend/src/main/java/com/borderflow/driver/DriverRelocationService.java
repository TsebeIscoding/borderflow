package com.borderflow.driver;

import com.borderflow.common.DriverNotFoundException;
import com.borderflow.common.InvalidDriverMoveException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Same shape as VehicleRelocationService. */
@Service
public class DriverRelocationService {

    private final DriverProfileRepository driverProfileRepository;
    private final DriverAvailabilityRepository driverAvailabilityRepository;
    private final String thisSiteId;

    public DriverRelocationService(
            DriverProfileRepository driverProfileRepository,
            DriverAvailabilityRepository driverAvailabilityRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.driverProfileRepository = driverProfileRepository;
        this.driverAvailabilityRepository = driverAvailabilityRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public DriverRelocationResponse relocate(UUID driverId, DriverRelocationRequest request) {
        driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));

        DriverAvailability availability = driverAvailabilityRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));

        if (!thisSiteId.equals(availability.getCurrentSiteId())) {
            throw new InvalidDriverMoveException(
                    "Driver " + driverId + " is currently at '" + availability.getCurrentSiteId() +
                            "', not '" + thisSiteId + "' -- cannot relocate a driver this site doesn't hold");
        }

        long newLamportTs = availability.getLamportTs() + 1;
        availability.relocateTo(request.toSiteId(), newLamportTs);
        driverAvailabilityRepository.save(availability);

        return new DriverRelocationResponse(driverId, thisSiteId, request.toSiteId(), newLamportTs);
    }
}

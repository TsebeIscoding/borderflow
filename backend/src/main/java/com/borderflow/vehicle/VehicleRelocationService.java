package com.borderflow.vehicle;

import com.borderflow.common.InvalidVehicleMoveException;
import com.borderflow.common.VehicleNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Same shape as ContainerRelocationService -- see its class javadoc for the design gaps that carry over here too. */
@Service
public class VehicleRelocationService {

    private final VehicleProfileRepository vehicleProfileRepository;
    private final VehicleAvailabilityRepository vehicleAvailabilityRepository;
    private final String thisSiteId;

    public VehicleRelocationService(
            VehicleProfileRepository vehicleProfileRepository,
            VehicleAvailabilityRepository vehicleAvailabilityRepository,
            @Value("${site.id}") String thisSiteId
    ) {
        this.vehicleProfileRepository = vehicleProfileRepository;
        this.vehicleAvailabilityRepository = vehicleAvailabilityRepository;
        this.thisSiteId = thisSiteId;
    }

    @Transactional
    public VehicleRelocationResponse relocate(UUID vehicleId, VehicleRelocationRequest request) {
        vehicleProfileRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        VehicleAvailability availability = vehicleAvailabilityRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        if (!thisSiteId.equals(availability.getCurrentSiteId())) {
            throw new InvalidVehicleMoveException(
                    "Vehicle " + vehicleId + " is currently at '" + availability.getCurrentSiteId() +
                            "', not '" + thisSiteId + "' -- cannot relocate a vehicle this site doesn't hold");
        }

        long newLamportTs = availability.getLamportTs() + 1;
        availability.relocateTo(request.toSiteId(), newLamportTs);
        vehicleAvailabilityRepository.save(availability);

        return new VehicleRelocationResponse(vehicleId, thisSiteId, request.toSiteId(), newLamportTs);
    }
}

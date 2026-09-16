package com.borderflow.vehicle;

import com.borderflow.common.VehicleNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Same auth shape as ContainerController and TripController -- see either for the reasoning. */
@RestController
@RequestMapping("/api/vehicles")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class VehicleController {

    private final VehicleProfileRepository vehicleProfileRepository;
    private final VehicleAvailabilityRepository vehicleAvailabilityRepository;
    private final VehicleRelocationService relocationService;

    public VehicleController(
            VehicleProfileRepository vehicleProfileRepository,
            VehicleAvailabilityRepository vehicleAvailabilityRepository,
            VehicleRelocationService relocationService
    ) {
        this.vehicleProfileRepository = vehicleProfileRepository;
        this.vehicleAvailabilityRepository = vehicleAvailabilityRepository;
        this.relocationService = relocationService;
    }

    @GetMapping
    public List<VehicleSummaryResponse> listVehicles() {
        return vehicleProfileRepository.findAll().stream()
                .map(profile -> vehicleAvailabilityRepository.findById(profile.getVehicleId())
                        .map(availability -> VehicleSummaryResponse.from(profile, availability))
                        .orElse(null))
                .filter(v -> v != null)
                .toList();
    }

    @GetMapping("/{vehicleId}")
    public VehicleSummaryResponse getVehicle(@PathVariable UUID vehicleId) {
        VehicleProfile profile = vehicleProfileRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        VehicleAvailability availability = vehicleAvailabilityRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        return VehicleSummaryResponse.from(profile, availability);
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping("/{vehicleId}/relocate")
    public ResponseEntity<VehicleRelocationResponse> relocate(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleRelocationRequest request
    ) {
        return ResponseEntity.ok(relocationService.relocate(vehicleId, request));
    }
}

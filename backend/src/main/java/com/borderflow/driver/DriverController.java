package com.borderflow.driver;

import com.borderflow.common.DriverNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Same auth shape as VehicleController. */
@RestController
@RequestMapping("/api/drivers")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class DriverController {

    private final DriverProfileRepository driverProfileRepository;
    private final DriverAvailabilityRepository driverAvailabilityRepository;
    private final DriverRelocationService relocationService;

    public DriverController(
            DriverProfileRepository driverProfileRepository,
            DriverAvailabilityRepository driverAvailabilityRepository,
            DriverRelocationService relocationService
    ) {
        this.driverProfileRepository = driverProfileRepository;
        this.driverAvailabilityRepository = driverAvailabilityRepository;
        this.relocationService = relocationService;
    }

    @GetMapping
    public List<DriverSummaryResponse> listDrivers() {
        return driverProfileRepository.findAll().stream()
                .map(profile -> driverAvailabilityRepository.findById(profile.getDriverId())
                        .map(availability -> DriverSummaryResponse.from(profile, availability))
                        .orElse(null))
                .filter(d -> d != null)
                .toList();
    }

    @GetMapping("/{driverId}")
    public DriverSummaryResponse getDriver(@PathVariable UUID driverId) {
        DriverProfile profile = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));
        DriverAvailability availability = driverAvailabilityRepository.findById(driverId)
                .orElseThrow(() -> new DriverNotFoundException(driverId));
        return DriverSummaryResponse.from(profile, availability);
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping("/{driverId}/relocate")
    public ResponseEntity<DriverRelocationResponse> relocate(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverRelocationRequest request
    ) {
        return ResponseEntity.ok(relocationService.relocate(driverId, request));
    }
}

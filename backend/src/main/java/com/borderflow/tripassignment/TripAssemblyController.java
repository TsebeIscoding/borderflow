package com.borderflow.tripassignment;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Both roles read; OPERATOR writes, at the site that holds the trip. */
@RestController
@RequestMapping("/api/trips/{tripId}")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class TripAssemblyController {

    private final TripAssemblyService service;

    public TripAssemblyController(TripAssemblyService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping("/vehicle/{vehicleId}")
    public ResponseEntity<Void> assignVehicle(@PathVariable UUID tripId, @PathVariable UUID vehicleId) {
        service.assignVehicle(tripId, vehicleId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/vehicle")
    public ResponseEntity<Void> releaseVehicle(@PathVariable UUID tripId) {
        service.releaseVehicle(tripId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping("/driver/{driverId}")
    public ResponseEntity<Void> assignDriver(@PathVariable UUID tripId, @PathVariable UUID driverId) {
        service.assignDriver(tripId, driverId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @DeleteMapping("/driver")
    public ResponseEntity<Void> releaseDriver(@PathVariable UUID tripId) {
        service.releaseDriver(tripId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignments")
    public List<TripAssignmentResponse> history(@PathVariable UUID tripId) {
        return service.history(tripId);
    }
}

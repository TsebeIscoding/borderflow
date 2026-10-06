package com.borderflow.incident;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Same auth shape as MilestoneController. */
@RestController
@RequestMapping("/api/trips/{tripId}/incidents")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public List<IncidentResponse> listIncidents(@PathVariable UUID tripId) {
        return incidentService.forTrip(tripId);
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping
    public ResponseEntity<IncidentResponse> recordIncident(
            @PathVariable UUID tripId,
            @Valid @RequestBody IncidentCreateRequest request
    ) {
        return ResponseEntity.ok(incidentService.record(tripId, request));
    }
}

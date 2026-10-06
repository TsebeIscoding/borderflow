package com.borderflow.milestone;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Same auth shape as HandoverController -- OPERATOR to record, either role to read. */
@RestController
@RequestMapping("/api/trips/{tripId}/milestones")
@PreAuthorize("hasAnyRole('OPERATOR', 'AUDITOR')")
public class MilestoneController {

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping
    public List<MilestoneResponse> listMilestones(@PathVariable UUID tripId) {
        return milestoneService.forTrip(tripId);
    }

    @PreAuthorize("hasRole('OPERATOR')")
    @PostMapping
    public ResponseEntity<MilestoneResponse> recordMilestone(
            @PathVariable UUID tripId,
            @Valid @RequestBody MilestoneCreateRequest request
    ) {
        return ResponseEntity.ok(milestoneService.record(tripId, request));
    }
}
